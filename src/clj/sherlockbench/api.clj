(ns sherlockbench.api
  (:require [sherlockbench.config :refer [benchmark-version msg-limit]]
            [sherlockbench.queries :as q]
            [sherlockbench.problems :refer [problems]]
            [sherlockbench.validate-fn-args :refer [validate-and-coerce]]))

(defn start-anonymous-run
  "initialize database entries for an anonymous run"
  [{queryfn :queryfn}]
  (let [; get the pertinent subset of the problems and randomize the order
        problems' (shuffle (filter #(:demo (:tags %)) problems))
        run-id (queryfn (q/create-run! benchmark-version msg-limit))
        attempts (for [p problems'      ; 1 attempt per problem
                       :let [attempt (queryfn (q/create-attempt! run-id p))]]
                   {:attempt-id attempt
                    :fn-args (:args p)})]
   {:status 200
    :headers {"Content-Type" "application/json"}
    :body {:run-id run-id
           :attempts attempts}}))

(defn wrap-check-run
  "did they give us a valid run id?"
  [handler]
  (fn [{queryfn :queryfn
        {{:keys [run-id]} :form} :parameters :as request}]
    (if (queryfn (q/check-run run-id))
      ;; continue as if nothing happened
      (handler request)

      ;; break as they have an expired session
      {:status 412
       :headers {"Content-Type" "application/json"}
       :body {:error "your run appears to be invalid or expired"}})))

(defn wrap-validate-args
  "a middleware to validate the args of a test function"
  [handler]
  (fn [{queryfn :queryfn
        {{:keys [run-id args attempt-id]} :form} :parameters :as request}]
    (let [fn-name (queryfn (q/get-fn-name attempt-id))
          this-problem (first (filter #(= fn-name (:name- %)) problems))
          {:keys [valid? coerced]} (validate-and-coerce (:args this-problem) args)]
      (if valid?
        ; add the validated args and continue
        (handler (assoc request :validated-args coerced))

        ; break as these args are invalid
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body {:error "your arguments don't comply with the schema"}}
        ))))

(defn test-function
  "run the test"
  [{queryfn :queryfn}]
  (let []
   {:status 200
    :headers {"Content-Type" "application/json"}
    :body {:foo "bar"}}))
