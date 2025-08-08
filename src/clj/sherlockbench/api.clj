(ns sherlockbench.api
  (:require [sherlockbench.constants :refer [benchmark-version default-test-limit]]
            [sherlockbench.queries :as q]
            [sherlockbench.validate-fn-args :refer [validate-and-coerce]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [sherlockbench.utility :as util]
            [sherlockbench.random-investigation :as rand-inv]))

;; Common response helpers
(defn api-response
  "Create a standardized API response"
  ([status body]
   (when-let [error (:error body)]
     (prn error))
   {:status status
    :headers {"Content-Type" "application/json"
              "Access-Control-Allow-Origin" "*"}
    :body body})
  ([body]
   (api-response 200 body)))

(defn valid-uuid? [uuid]
  (try
    (java.util.UUID/fromString uuid)
    true
    (catch IllegalArgumentException _ false)
    (catch NullPointerException _ false)))

;; Problem lookup helpers
(defn get-problem-set
  "Get a set of problems by problem set keyword"
  [problems pset]
  (get-in (apply merge (vals problems)) [pset :problems]))

(defn get-problem-by-name
  "Find a problem in a problem list by name"
  [problems fn-name]
  (first (filter #(= fn-name (:name- %)) problems)))

(defn problems-by-run-id
  "Get problems associated with a run ID"
  [queryfn problems run-id]
  (let [pset-kw (keyword (queryfn (q/get-run-pset run-id)))]
    (get-problem-set problems pset-kw)))

(defn create-run
  "create a run and attempts"
  [queryfn problems client-id run-state pset-kw attempts-per-problem]
  (let [; get the pertinent subset of the problems
        run-type (case run-state
                   "pending" "official"
                   "started" "anonymous")
        problems' (flatten (repeat attempts-per-problem (get-problem-set problems pset-kw)))
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
                  (for [p problems'    ; 1 attempt per problem
                        :let [{:keys [id test_limit]} (queryfn (q/create-attempt! run-id p))]]
                    {:attempt-id id
                     :arg-spec (:args p)
                     :output-type (:output-type p)
                     :test-limit test_limit
                     :attempts-remaining test_limit}))]
    [run-id attempts]))

(defn pending-run? [{queryfn :queryfn
                     {:keys [run-id]} :body}]
  (api-response {:response (queryfn (q/pending-run? run-id))}))

(defn start-anonymous-run
  "initialize database entries for an anonymous run"
  [{queryfn :queryfn
    problems :problems
    anonymous-runs-allowed :anonymous-runs-allowed
    {:keys [client-id problem-set attempts-per-problem]} :body}]
  (if-not anonymous-runs-allowed
    (api-response 403 {:error "Anonymous runs are disabled. Please use an existing run ID."})
    
    (let [pset-kw (keyword problem-set)
          attempts-count (if (and attempts-per-problem (integer? attempts-per-problem))
                           attempts-per-problem
                           1)]
      (if-not (contains? (set (apply concat (map keys (vals problems)))) pset-kw)
        (api-response 400 {:error (str "Invalid exam set: " problem-set)})
        
        (let [[run-id attempts] (create-run queryfn problems client-id "started" pset-kw attempts-count)]
          (api-response {:run-id run-id
                         :run-type "anonymous"
                         :benchmark-version benchmark-version
                         :attempts attempts}))))))

(defn get-problem-by-name
  "Find a problem in a problem list by name"
  [problems fn-name]
  (first (filter #(= fn-name (:name- %)) problems)))


(defn start-competition-run
  "In this one we will use an pre-existing run-id.
   Update datetime_start, run_state and client_id.
   Retrieve the attempts in random order."
  [{queryfn :queryfn
    problems :problems
    {:keys [existing-run-id client-id]} :body}]
  (if (queryfn (q/started? existing-run-id))
    (api-response 412 {:error "this run has already been started"})

    (let [attempts (queryfn (q/start-run! existing-run-id client-id)) ; list of maps 
          problems' (problems-by-run-id queryfn problems existing-run-id)
          ;; map over attempts, replacing :function_name with fn args
          attempts' (for [{:keys [id function_name test_limit]} attempts]
                      (let [p (get-problem-by-name problems' function_name)]
                        {:attempt-id id
                         :arg-spec (:args p)
                         :output-type (:output-type p)
                         :test-limit test_limit
                         :attempts-remaining test_limit}))]

      (api-response {:run-id existing-run-id
                     :run-type "competition"
                     :benchmark-version benchmark-version
                     :attempts attempts'}))))

(defn start-run
  "check if there is a run-id specified and decide which fn to call"
  [{{:keys [existing-run-id problem-set]} :body :as request}]
  (cond
    (valid-uuid? existing-run-id) (start-competition-run request)
    (util/not-empty-string? problem-set) (start-anonymous-run request)
    :else (api-response 400 {:error "Either an existing run ID or a problem set must be specified"})))

(defn wrap-check-run
  "did they give us a valid run id?"
  [handler]
  (fn [{queryfn :queryfn
        {:keys [run-id]} :body :as request}]
    (if (queryfn (q/active-run? run-id))
      ;; continue as if nothing happened
      (handler request)

      ;; break as they have an expired session
      (api-response 412 {:error "your run appears to be invalid or expired"}))))

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
      (api-response 412 {:error "your attempt-id doesn't match your run-id"}))))

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
          (api-response 400 {:error "your arguments don't comply with the schema"}))))))

(defn apply-fn
  [queryfn attempt-id problem validated-args]
  (let [problem-state (queryfn (q/get-problem-state attempt-id))
        problem-fn (if (not (empty? problem-state)) ; if we're using function state
                         (partial (:function problem) problem-state)
                         (:function problem))]
    (try (apply problem-fn validated-args)
         (catch Exception e
           ;; (println e)
           "Exception"))))

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
      (api-response 400 {:error (format "you have reached the test limit of %d for this problem" test_limit)})
      
      (true? started-verifications)
      (api-response 400 {:error "you cannot test the function after you start the validations"})
      
      :else
      (let [problems' (problems-by-run-id queryfn problems run-id)
            problem (get-problem-by-name problems' fn-name)
            output (apply-fn queryfn attempt-id problem validated-args)]

        (api-response {:output output
                       :attempts_remaining remaining-attempts
                       :test_limit test_limit})))))

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
    {:keys [run-id attempt-id]} :body}]

  (queryfn (q/started-verifications! attempt-id)) ; record we've started

  (let [next-verification (first (queryfn (q/get-verifications attempt-id)))
        problems' (problems-by-run-id queryfn problems run-id)
        output-type (:output-type (get-problem-by-name problems' fn-name))]
    (if (nil? next-verification)
      (api-response {:status "done"
                     :next-verification nil})
      (api-response {:status "success"
                     :next-verification next-verification
                     :output-type output-type}))))

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
      (api-response 400 {:error "you're done"})
      (let [output (apply-fn queryfn attempt-id problem this-verification)]
        (if (=normalized prediction output)
          (if remaining-verifications
            ;; success with more
            (api-response {:status "correct"
                           :next-verification (first remaining-verifications)
                           :output-type output-type})

            ;; all done
            (do
              (queryfn (q/attempt-success! attempt-id))
              (api-response {:status "done"
                             :next-verification nil})))
          ;; failure
          (do
            (queryfn (q/attempt-failure! attempt-id))
            (api-response {:status "wrong"
                           :next-verification nil})))))))

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
    
    (api-response {:run-time (str total-run-time)
                   :score final-score
                   :percent score-percent
                   :problem-names problem-names})))

(defn list-problem-sets
  "List available problem sets, providing only the category names, problem-set ids, and names.
   Does not expose the actual problems."
  [{problems :problems anonymous-runs-allowed :anonymous-runs-allowed}]
  (let [problem-sets-by-category 
        (for [[category-name category-sets] problems]
          [category-name
           (for [[problem-set-key {:keys [name]}] category-sets]
             {:id (util/problem-set-key-to-string problem-set-key)
              :name name})])]

    (if anonymous-runs-allowed
      (api-response {:problem-sets (into {} problem-sets-by-category)})
      (api-response 403 {:error "Anonymous runs are disabled. Please use an existing run ID."}))))

(defn wrap-set-developer
  "Middleware that marks a run as developer type, indicating it's no longer
   an official benchmark run because developer operations were used."
  [handler]
  (fn [{queryfn :queryfn
        {:keys [run-id]} :body :as request}]
    ;; Update run_type to 'developer'
    (queryfn (q/set-run-type-to-developer! run-id))
    
    ;; Continue with the handler
    (handler request)))

(defn reset-attempt
  "Reset all mutable fields for an attempt to their initial state.
   This allows the user to retry an attempt, but marks the run as developer type."
  [{queryfn :queryfn
    problems :problems
    {:keys [run-id attempt-id]} :body}]
  
  ;; Get the original verifications from the problem definition
  (let [fn-name (queryfn (q/get-fn-name attempt-id))
        problems' (problems-by-run-id queryfn problems run-id)
        problem (get-problem-by-name problems' fn-name)]
    
    ;; Reset all mutable fields for the attempt
    (queryfn (q/reset-attempt! attempt-id problem))
    
    (api-response {:status "success"
                   :message "Attempt has been reset"})))

(defn get-problem-names
  "Get the names of all problems for a given run."
  [{queryfn :queryfn
    {:keys [run-id]} :body}]
  (let [problem-names (queryfn (q/get-names-and-ids run-id))]
    (api-response {:problem-names problem-names})))

(defn random-investigation
  "Generate random inputs for the function, evaluate them and return."
  [{queryfn :queryfn
    problems :problems
    fn-name :fn-name
    {:keys [run-id attempt-id]} :body}]

  (let [problems' (problems-by-run-id queryfn problems run-id)
        {:keys [args function test-limit]} (get-problem-by-name problems' fn-name)
        output (rand-inv/random-fn-inputs args function test-limit)]

        (api-response {:output output})))
