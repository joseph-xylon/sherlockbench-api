(ns sherlockbench.api
  (:require [sherlockbench.config :refer [benchmark-version default-test-limit]]
            [sherlockbench.queries :as q]
            [sherlockbench.validate-fn-args :refer [validate-and-coerce]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

(defn valid-uuid? [uuid]
  (try
    (java.util.UUID/fromString uuid)
    true
    (catch IllegalArgumentException _ false)
    (catch NullPointerException _ false)))

(defn filter-by-problem-set
  "Filter problems by a problem set configuration (subset selection)"
  [problem-set-id all-problems config]
  (if (nil? problem-set-id)
    all-problems
    (let [problem-set-config (get-in config [:problem-sets problem-set-id])
          tags (get-in problem-set-config [:problems :tags] #{})
          names (get-in problem-set-config [:problems :names] #{})]
      
      ;; Select problems that match either the tags or the names
      (filter (fn [problem]
                (or (some tags (:tags problem))
                    (contains? names (:name- problem))))
              all-problems))))

(defn tag-name->problem-set
  "Try to match a simple name like 'easy3' to a proper problem set keyword"
  [tag-name config]
  (let [all-problem-sets (keys (get config :problem-sets))
        ;; Check for exact match first
        exact-match (first (filter #(= (name %) tag-name) all-problem-sets))]
    
    (if exact-match
      exact-match
      ;; Try to find a match with namespaced keywords that end with this name
      (first (filter #(and (keyword? %) 
                          (clojure.string/ends-with? (str %) (str "/" tag-name)))
                    all-problem-sets)))))

(defn filter-problems
  "Select problems based on run type and optional problem set"
  [run-type problems problem-set config]
  (println "Original problem-set:" problem-set)
  (let [resolved-set (if (and (string? problem-set) (not (re-find #"/" problem-set)))
                      ;; If it's a simple string with no slash, try to resolve it to a proper problem set
                      (or (tag-name->problem-set problem-set config)
                          ;; For direct tag filtering
                          (keyword "problems" problem-set)
                          problem-set)
                      problem-set)]
    
    (println "Resolved to problem-set:" resolved-set)
    
    (cond
      ;; Case 1: Anonymous run with no problem set - use only problems from sherlockbench.problems namespace
      (and (= run-type "anonymous") (nil? resolved-set))
      (filter #(contains? (:tags %) :problems) problems)
      
      ;; Case 2: Anonymous run with problem set - special handling for string tag names in anonymous runs
      (and (= run-type "anonymous") (string? resolved-set) (not (re-find #"/" resolved-set)))
      (let [tag-keyword (keyword "problems" resolved-set)
            problems-namespace (filter #(contains? (:tags %) :problems) problems)]
        (println "Using direct tag filtering with tag:" tag-keyword)
        (filter #(contains? (:tags %) tag-keyword) problems-namespace))

      ;; Case 3: Anonymous run with problem set - apply problem set filter but only within sherlockbench.problems
      (= run-type "anonymous")
      (let [keyword-set (if (keyword? resolved-set) resolved-set (keyword resolved-set))
            problems-namespace (filter #(contains? (:tags %) :problems) problems)]
        (filter-by-problem-set keyword-set problems-namespace config))
      
      ;; Case 4: Official run with problem set - apply only problem set filter
      (and (= run-type "official") (not (nil? resolved-set)))
      (let [keyword-set (if (keyword? resolved-set) resolved-set (keyword resolved-set))]
        (filter-by-problem-set keyword-set problems config))
      
      ;; Case 5: Official run with no problem set - use all problems
      :else problems)))

(defn create-run
  "create a run and attempts"
  [queryfn problems client-id run-type run-state subset & [provided-config]]
  (println "Creating run with subset:" subset)
  (println "Run type:" run-type)
  
  ;; Debug available problem tags
  (when (and subset (= run-type "anonymous"))
    (let [p-tags (filter #(contains? (:tags %) :problems) problems)]
      (println "All tags available in the problems namespace:")
      (doseq [p p-tags]
        (println "  -" (:name- p) ":" (:tags p)))))
  
  (let [; get the pertinent subset of the problems
        problems' (filter-problems run-type problems subset provided-config)
        _ (println "Filtered to" (count problems') "problems")
        _ (when (< (count problems') 1)
            (println "WARNING: No problems were selected with this filter!"))
        
        ;; Print problem names for debugging
        _ (println "Selected problems:" (mapv :name- problems'))
        
        now (java.time.LocalDateTime/now)
        config {:subset subset}
        run-id (queryfn (q/create-run! benchmark-version client-id run-type config run-state (when (= run-type "anonymous") now)))
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
  [{queryfn :queryfn
    problems :problems
    config :config
    {:keys [client-id subset]} :body}]
  (let [[run-id attempts] (create-run queryfn problems client-id "anonymous" "started" subset config)]

    {:status 200
     :headers {"Content-Type" "application/json"
               "Access-Control-Allow-Origin" "*"}
     :body {:run-id run-id
            :run-type "anonymous"
            :benchmark-version benchmark-version
            :attempts attempts}}))

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
          ;; map over attempts, replacing :function_name with fn args
          attempts' (for [{:keys [id function_name test_limit]} attempts]
                      {:attempt-id id
                       :arg-spec (->> problems
                                     (filter #(= (:name- %) function_name))
                                     first
                                     :args)
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
  [{{:keys [existing-run-id]} :body :as request}]
  (if (valid-uuid? existing-run-id)
    (start-competition-run request)
    (start-anonymous-run request)))

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

(defn get-problem-by-name
  [problems fn-name]
  (first (filter #(= fn-name (:name- %)) problems)))

(defn wrap-validate-args
  "a middleware to validate the args of a test function"
  [handler]
  (fn [{queryfn :queryfn
        problems :problems
        {:keys [run-id attempt-id args]} :body
        fn-name :fn-name :as request}]
    (let [this-problem (get-problem-by-name problems fn-name)
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
    {:keys [attempt-id]} :body}]

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
      (let [problem (get-problem-by-name problems fn-name)
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
    {:keys [attempt-id prediction]} :body}]

  (let [problem (get-problem-by-name problems fn-name)
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
