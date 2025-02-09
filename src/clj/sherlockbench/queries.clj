(ns sherlockbench.queries
  ;; exclude these core clojure functions
  (:refer-clojure :exclude [distinct filter for group-by into partition-by set update])
  
  (:require [honey.sql :as sql]
            [honey.sql.helpers :refer :all]  ;; shadows core functions
            [clojure.core :as c]  ;; so we can still access core functions
            [next.jdbc :as jdbc]
            [buddy.hashers :as hashers]
            [clojure.data.json :as json]
            [clojure.set :as set]))

(defn clean-ns [m]
  (clojure.core/into {} (map (fn [[k v]] [(keyword (name k)) v]) m)))


;; the core namespace will closure over this with the connection
(defn execute-query
  "takes a connection, one or more HoneySQL queries, and a post-processor function"
  [conn qdata & [debug]]
  (let [[processor & queries] (reverse qdata)
        formatted-queries (map sql/format (reverse queries))]
    (when debug (doseq [formatted-query formatted-queries]
                    (println (str "formatted-query: " formatted-query))))

    (apply processor (map #(jdbc/execute! conn %) formatted-queries))))

(defn create-user
  "hashes the password"
  [login password]
  [(-> (insert-into :users)
       (values [{:login login
                 :password (hashers/derive password)}]))

   identity])

(defn authenticate-user
  "we return true or false indicating authentication success"
  [login password]
  [(-> (select [:*])
       (from :users)
       (where [:= :login login]))

   (fn [[{hashed :users/password}]]
     (hashers/check password hashed))])

(defn create-run!
  [benchmark-version client-id run-type config run-state starttime]
  [(-> (insert-into :runs)
       (values [{:benchmark_version benchmark-version
                 :config [:cast (json/write-str config) :jsonb]
                 :client_id client-id
                 :run_type [:cast run-type :run_type_type]
                 :run-state [:cast run-state :run_state_type]
                 :datetime_start starttime}])
       (returning :id))

   #(:runs/id (first %))])

(defn start-run!
  [run-id client-id]
  [(-> (update :runs)
       (set {:client_id client-id
             :datetime_start [:now]
             :run_state [:cast "started" :run_state_type]})
       (where [:= :id [:cast run-id :uuid]]))
   (-> (select :id :function_name)
       (from :attempts)
       (where [:= :run_id [:cast run-id :uuid]])
       (order-by [[:random]]))

   (fn [_ v]
     (map (fn [m] (reduce-kv #(assoc %1 (keyword (name %2)) %3) {} m)) v))])

(defn create-attempt!
  [run-id problem]
  [(-> (insert-into :attempts)
       (values [{:run_id run-id
                 :function_name (:name- problem)
                 :verifications [:cast (json/write-str (:verifications problem)) :jsonb]}])
       (returning :id))

   #(:attempts/id (first %))])

(defn check-run
  "return true or false indicating if they have a valid run_id"
  [run-id]
  [(-> (select [:*])
       (from :runs)
       (where [:and
               [:= :id [:cast run-id :uuid]]
               [:> [:+ :datetime_start [:interval "1 day"]] [:now]]
               [:= :final_score nil]]))

   #(not (empty? %))])

(defn get-fn-name
  "Given an attempt UUID, returns the associated function_name."
  [attempt-id]
  [(-> (select :function_name)
       (from :attempts)
       (where [:= :id [:cast attempt-id :uuid]]))

   #(:attempts/function_name (first %))])

(defn get-names-and-ids
  "returns all attempt ids and names"
  [run-id]
  [(-> (select :id :function_name)
       (from :attempts)
       (where [:= :run_id [:cast run-id :uuid]]))

   #(mapv clean-ns %)])

(defn attempt-valid?
  "given both a run id and attempt id, check the attempt id matches to the run"
  [run-id attempt-id]
  [(-> (select [:*])
       (from :attempts)
       (where [:and
               [:= :run-id [:cast run-id :uuid]]
               [:= :id [:cast attempt-id :uuid]]]))

   #(not (empty? %))])

(defn increment-fn-calls
  "Increments the fn_calls column for a given attempt ID."
  [attempt-id]
  [(-> (update :attempts)
       (set {:fn_calls [:+ :fn_calls 1]})
       (where [:= :id [:cast attempt-id :uuid]])
       (returning :fn_calls))

   #(:attempts/fn_calls (first %))])

(defn started-verifications?
  "Given an attempt UUID, returns if it started verifications yet."
  [attempt-id]
  [(-> (select [:started_verifications])
       (from :attempts)
       (where [:= :id [:cast attempt-id :uuid]]))

   #(:attempts/started_verifications (first %))])

(defn get-verifications
  "Given an attempt UUID, returns the associated validations"
  [attempt-id]
  [(-> (select [:verifications])
       (from :attempts)
       (where [:= :id [:cast attempt-id :uuid]]))

   #(json/read-str (.getValue (:attempts/verifications (first %))))])

(defn started-verifications!
  "Given an attempt UUID, set the started_verifications field."
  [attempt-id]
  [(-> (update :attempts)
       (set {:started_verifications true})
       (where [:= :id [:cast attempt-id :uuid]]))

   identity])

(defn save-verifications!
  "Given an attempt UUID, set the started_verifications field."
  [attempt-id v]
  [(-> (update :attempts)
       (set {:verifications [:cast (json/write-str v) :jsonb]})
       (where [:= :id [:cast attempt-id :uuid]]))

   identity])

(defn attempt-failure!
  "Given an attempt UUID, set the attempt failed."
  [attempt-id]
  [(-> (update :attempts)
       (set {:result_value "failure"
             :verifications [:cast (json/write-str nil) :jsonb]})
       (where [:= :id [:cast attempt-id :uuid]]))

   identity])

(defn attempt-success!
  "Given an attempt UUID, set the attempt succeeded."
  [attempt-id]
  [(-> (update :attempts)
       (set {:result_value "success"})
       (where [:= :id [:cast attempt-id :uuid]]))

   identity])

(defn get-run-time
  [run-id]
  [(-> (select :datetime_start [[:age [:now] :datetime_start] :total_run_time])
       (from :runs)
       (where [:= :id [:cast run-id :uuid]]))

   #(:total_run_time (first %))])

(defn get-final-score
  [run-id]
  [(-> (select [[:count :*] :count])
       (from :attempts)
       (where [:and
               [:= :run_id [:cast run-id :uuid]]
               [:= :result_value "success"]]))

   (-> (select [[:count :*] :count])
       (from :attempts)
       (where [:= :run_id [:cast run-id :uuid]]))

   #(hash-map :numerator (:count (first %1)) :denominator (:count (first %2)))])

(defn save-results!
  [run-id total-run-time final-score score-percent]
  [(-> (update :runs)
       (set {:total_run_time total-run-time
             :final_score [:cast (json/write-str final-score) :jsonb]
             :score_percent score-percent
             :run_state [:cast "complete" :run_state_type]})
       (where [:= :id [:cast run-id :uuid]]))

   identity])

(defn parse-psql-json
  [m]
  (when m
    (json/read-str (.getValue m))))

(defn list-runs
  []
  [(-> (select :*)
       (from :runs))

   (fn [xs] (->> xs
                 (map (fn [m] (clojure.core/update m :runs/config parse-psql-json)))
                 (map (fn [m] (clojure.core/update m :runs/final_score parse-psql-json)))))])

(defn delete-run!
  "returns true or false"
  [run-id]
  [(-> (delete-from :runs)
       (where [:= :id [:cast run-id :uuid]]))

   #(not= 0 (:next.jdbc/update-count (first %)))])
