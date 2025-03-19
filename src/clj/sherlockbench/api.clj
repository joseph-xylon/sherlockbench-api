(ns sherlockbench.api
  (:require [sherlockbench.config :refer [benchmark-version default-test-limit]]
            [sherlockbench.queries :as q]
            [sherlockbench.validate-fn-args :refer [validate-and-coerce]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [sherlockbench.utility :as util]))

(defn valid-uuid? [uuid]
  (try
    (java.util.UUID/fromString uuid)
    true
    (catch IllegalArgumentException _ false)
    (catch NullPointerException _ false)))

(defn get-problem-set
  [problems pset]
  (get-in (apply merge (vals problems)) [pset :problems]))

(defn create-run
  "create a run and attempts"
  [queryfn problems client-id run-state pset-kw]
  (let [; get the pertinent subset of the problems
        run-type (case run-state
                   "pending" "official"
                   "started" "anonymous")
        problems' (get-problem-set problems pset-kw)
        now (java.time.LocalDateTime/now)
        config {}
        run-id (queryfn (q/create-run! benchmark-version
                                       client-id
                                       run-type
                                       (util/problem-set-key-to-string pset-kw)
                                       config
                                       run-state
                                       (when (= run-type "anonymous") now)))
        attempts (doall
                  (for [p problems'     ; 1 attempt per problem
                        :let [{:keys [id test_limit]} (queryfn (q/create-attempt! run-id p))]]
                    {:attempt-id id
                     :arg-spec (:args p)
                     :test-limit test_limit
                     :attempts-remaining test_limit}))]
    [run-id attempts]))

(defn pending-run? [{queryfn :queryfn
                     {:keys [run-id]} :body}]
  {:status 200
   :headers {"Content-Type" "application/json"
             "Access-Control-Allow-Origin" "*"}
   :body {:response (queryfn (q/pending-run? run-id))}})

(defn start-anonymous-run
  "initialize database entries for an anonymous run"
  ;; TODO I'm pretty sure this is broken
  [{queryfn :queryfn
    problems :problems
    {:keys [client-id problem-set]} :body}]
  (let [pset-kw (keyword problem-set)]
    (if-not (contains? (set (apply concat (map keys (vals problems)))) pset-kw)
      {:status 400
       :headers {"Content-Type" "application/json"
               "Access-Control-Allow-Origin" "*"}
       :body {:error (str "Invalid exam set: " problem-set)}}
      
      (let [[run-id attempts] (create-run queryfn problems client-id "started" pset-kw)]

        {:status 200
         :headers {"Content-Type" "application/json"
                   "Access-Control-Allow-Origin" "*"}
         :body {:run-id run-id
                :run-type "anonymous"
                :benchmark-version benchmark-version
                :attempts attempts}}))))

(defn get-problem-by-name
  [problems fn-name]
  (first (filter #(= fn-name (:name- %)) problems)))

(defn problems-by-run-id
  [queryfn problems run-id]
  (let [pset-kw (keyword (queryfn (q/get-run-pset run-id)))]
    (get-in (apply merge (vals problems)) [pset-kw :problems])))

(defn start-competition-run
  "In this one we will use an pre-existing run-id.
   Update datetime_start, run_state and client_id.
   Retrieve the attempts in random order."
  [{queryfn :queryfn
    problems :problems
    {:keys [existing-run-id client-id]} :body}]
  (if (queryfn (q/started? existing-run-id))
    {:status 412
     :headers {"Content-Type" "application/json"
               "Access-Control-Allow-Origin" "*"}
     :body {:error "this run has already been started"}}

    (let [attempts (queryfn (q/start-run! existing-run-id client-id)) ; list of maps 
          problems' (problems-by-run-id queryfn problems existing-run-id)
          ;; map over attempts, replacing :function_name with fn args
          attempts' (for [{:keys [id function_name test_limit]} attempts]
                      {:attempt-id id
                       :arg-spec (:args (get-problem-by-name problems' function_name))
                       :test-limit test_limit
                       :attempts-remaining test_limit})]

      {:status 200
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"}
       :body {:run-id existing-run-id
              :run-type "competition"
              :benchmark-version benchmark-version
              :attempts attempts'}})))

(defn start-run
  "check if there is a run-id specified and decide which fn to call"
  [{{:keys [existing-run-id problem-set]} :body :as request}]
  (cond
    (valid-uuid? existing-run-id) (start-competition-run request)
    (util/not-empty-string? problem-set) (start-anonymous-run request)))

(defn wrap-check-run
  "did they give us a valid run id?"
  [handler]
  (fn [{queryfn :queryfn
        {:keys [run-id]} :body :as request}]
    (if (queryfn (q/active-run? run-id))
      ;; continue as if nothing happened
      (handler request)

      ;; break as they have an expired session
      {:status 412
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"}
       :body {:error "your run appears to be invalid or expired"}})))

(defn wrap-check-attempt
  "did they give us a valid attempt id?"
  [handler]
  (fn [{queryfn :queryfn
        {:keys [run-id attempt-id]} :body :as request}]
    (if (queryfn (q/attempt-valid? run-id attempt-id))
      ;; add the fn-name to the request as it's handy later
      (let [fn-name (queryfn (q/get-fn-name attempt-id))]
        (handler (assoc request :fn-name fn-name)))

      ;; break as they have an expired session
      {:status 412
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"}
       :body {:error "your attempt-id doesn't match your run-id"}})))

(defn wrap-validate-args
  "a middleware to validate the args of a test function"
  [handler]
  (fn [{queryfn :queryfn
        problems :problems
        {:keys [run-id attempt-id args]} :body
        fn-name :fn-name :as request}]
    (let [problems' (problems-by-run-id queryfn problems run-id)
          this-problem (get-problem-by-name problems' fn-name)
          {:keys [valid? coerced errors]} (validate-and-coerce (:args this-problem) args)]
      (if valid?
        ;; add the validated args and continue
        (handler (assoc request
                        :validated-args coerced
                        :fn-name fn-name))

        ;; break as these args are invalid
        (do
          (log/info errors)

          {:status 400
           :headers {"Content-Type" "application/json"
                     "Access-Control-Allow-Origin" "*"}
           :body {:error "your arguments don't comply with the schema"}})))))

(defn apply-fn
  [problem validated-args]
  (try (apply (:function problem) validated-args)
       (catch Exception e
         "Exception")))

(defn test-function
  "run the test"
  [{queryfn :queryfn
    problems :problems
    validated-args :validated-args
    fn-name :fn-name
    {:keys [run-id attempt-id]} :body}]

  (let [{:keys [fn_calls test_limit]} (queryfn (q/increment-fn-calls attempt-id))
        started-verifications (queryfn (q/started-verifications? attempt-id))
        remaining-attempts (- test_limit fn_calls)]
    (cond
      (> fn_calls test_limit)
      {:status 400
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"}
       :body {:error (format "you have reached the test limit of %d for this problem" test_limit)}}
      (true? started-verifications)
      {:status 400
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"}
       :body {:error "you cannot test the function after you start the validations"}}
      
      :else
      (let [problems' (problems-by-run-id queryfn problems run-id)
            problem (get-problem-by-name problems' fn-name)
            output (apply-fn problem validated-args)]

        {:status 200
         :headers {"Content-Type" "application/json"
                   "Access-Control-Allow-Origin" "*"}
         :body {:output output
                :attempts_remaining remaining-attempts
                :test_limit test_limit}}))))

(defn wrap-record-started [handler]
  (fn [{queryfn :queryfn
        {:keys [attempt-id]} :body :as request}]

    (queryfn (q/started-verifications! attempt-id))

    (handler request)))

(defn pop-verification
  [queryfn attempt-id]
  (let [[this & rest] (queryfn (q/get-verifications attempt-id))]
    (queryfn (q/save-verifications! attempt-id rest))
    [this rest]))

(defn next-verification
  "just give them the next verification for their attempt"
  [{queryfn :queryfn
    fn-name :fn-name
    problems :problems
    {:keys [attempt-id]} :body}]

  (queryfn (q/started-verifications! attempt-id)) ; record we've started

  (let [next-verification (first (queryfn (q/get-verifications attempt-id)))
        output-type (:output-type (get-problem-by-name problems fn-name))]
    (if (nil? next-verification)
      {:status 200
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"}
       :body {:status "done"
              :next-verification nil}}
      {:status 200
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"}
       :body {:status "success"
              :next-verification next-verification
              :output-type output-type}})))

(defn =normalized
  "normalize everything to a string for comparrison"
  [& all]
  (apply = (map str all)))

(defn attempt-verification
  "let the client attempt a verification"
  [{queryfn :queryfn
    fn-name :fn-name
    problems :problems
    {:keys [run-id attempt-id prediction]} :body}]

  (let [problems' (problems-by-run-id queryfn problems run-id)
        problem (get-problem-by-name problems' fn-name)
        output-type (:output-type problem)
        [this-verification remaining-verifications] (pop-verification queryfn attempt-id)]
    (if (nil? this-verification)
      {:status 400
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"}
       :body {:error "you're done"}}
      (let [output (apply-fn problem this-verification)]
        (if (=normalized prediction output)
          (if remaining-verifications
            ;; success with more
            {:status 200
             :headers {"Content-Type" "application/json"
                       "Access-Control-Allow-Origin" "*"}
             :body {:status "correct"
                    :next-verification (first remaining-verifications)
                    :output-type output-type}}

            ;; all done
            (do
              (queryfn (q/attempt-success! attempt-id))
              {:status 200
               :headers {"Content-Type" "application/json"
                         "Access-Control-Allow-Origin" "*"}
               :body {:status "done"
                      :next-verification nil}})
            )
          ;; failure
          (do
            (queryfn (q/attempt-failure! attempt-id))
            {:status 200
             :headers {"Content-Type" "application/json"
                       "Access-Control-Allow-Origin" "*"}
             :body {:status "wrong"
                    :next-verification nil}}))))))

(defn complete-run
  "They tell us they have completed the run."
  [{queryfn :queryfn
    fn-name :fn-name
    {:keys [run-id]} :body}]
  (let [total-run-time (queryfn (q/get-run-time run-id))
        {:keys [numerator denominator] :as final-score} (queryfn (q/get-final-score run-id))
        score-percent (double (* 100 (/ numerator denominator)))
        problem-names (queryfn (q/get-names-and-ids run-id))]
    (queryfn (q/save-results! run-id total-run-time final-score score-percent))
    
    {:status 200
     :headers {"Content-Type" "application/json"
               "Access-Control-Allow-Origin" "*"}
     :body {:run-time (str total-run-time)
            :score final-score
            :percent score-percent
            :problem-names problem-names}}
    ))
