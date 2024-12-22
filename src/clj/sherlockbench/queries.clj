(ns sherlockbench.queries
  ;; exclude these core clojure functions
  (:refer-clojure :exclude [distinct filter for group-by into partition-by set update])
  
  (:require [honey.sql :as sql]
            [honey.sql.helpers :refer :all]  ;; shadows core functions
            [clojure.core :as c]  ;; so we can still access core functions
            [next.jdbc :as jdbc]
            [buddy.hashers :as hashers]
            [clojure.data.json :as json]))

;; the core namespace will closure over this with the connection
(defn execute-query
  "takes a connection, a HoneySQL query, and a post-processor function"
  [conn [query processor] & [debug]]
  (let [formatted-query (sql/format query)]
    (when debug (println (str "formatted-query: " formatted-query)))
    (-> (jdbc/execute! conn formatted-query)
        processor)))

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
  [benchmark-version msg-limit]
  [(-> (insert-into :runs)
       (values [{:benchmark_version benchmark-version
                 :config [:cast (json/write-str {:msg-limit msg-limit}) :jsonb]}])
       (returning :id))

   #(:runs/id (first %))])

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
               [:> [:+ :datetime_start [:interval "1 day"]] [:now]]]))

   #(not (empty? %))])

(defn get-fn-name
  "Given an attempt UUID, returns the associated function_name."
  [attempt-id]
  [(-> (select [:function_name])
       (from :attempts)
       (where [:= :id [:cast attempt-id :uuid]]))

   #(:attempts/function_name (first %))])
