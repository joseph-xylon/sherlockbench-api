(ns sherlockbench.routes
  (:require [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.memory :as memory]
            [ring.middleware.json :refer [wrap-json-body]]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [sherlockbench.handlers :as hl]
            [sherlockbench.api :as api]
            [sherlockbench.debug-middlewares :refer [wrap-debug-reqmap whenwrap log-path-middleware]]
            [ring.logger :as logger]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]))

(s/def ::id int?)
(s/def ::string string?)
(s/def ::anything any?)
(s/def ::anything-collection (s/coll-of any?))
;; This will be dynamically checked against the run-types from config in the handler
(s/def ::exam-sets string?)
(s/def ::vector-of-strings (s/coll-of string? :kind vector?))

(s/def ::uuid (s/and string? api/valid-uuid?))

(defn wrap-auth [handler]
  (fn [{:keys [uri query-string session] :as request}]
    (if (contains? session :user)
      ;; user is logged in. proceed
      (handler request)

      ;; user not logged in. redirect
      (let [redirect-url (java.net.URLEncoder/encode
                          (str uri "?" query-string)
                          "UTF-8")]
        {:status 303
         :headers {"Location" (str "/web/login?redirect=" redirect-url)}
         :body ""}))))

(defn output-to-json [handler]
  (fn [request]
    (let [output (handler request)]
      ;; (prn "output: " output)
      (update output :body json/write-str))))

(defn coerce-to-vec
  "Forms with checkboxes return either string or vec dependant on whether one or
  two are checked. Need to standardize as always string."
  [m field-specs]
  (reduce-kv (fn [acc key spec]
               (let [v (get m key)]
                 (assoc acc key
                        (if (and
                             (str/includes? spec "vector-of-")
                             (not (vector? v)))
                          [v]
                          v))))
             {}
             field-specs))

(defn validate-fields
  "Validate the keys in map `m` using the corresponding specs in `field-specs`.
  Returns an empty map if all validations pass; otherwise returns a map
  where each key maps to its explain-data error."
  [m field-specs]
  (reduce-kv (fn [errors key spec]
               (let [v (get m key)]
                 (if (s/valid? spec v)
                   errors
                   (assoc errors key (s/explain-data spec v)))))
             {}
             field-specs))

(defn wrap-validate-body [handler]
  (fn [request]
    (let [validation (get-in request [:reitit.core/match :data :post :validation])
          body (:body request)
          body-coerced (coerce-to-vec body validation)
          request' (assoc request :body body-coerced)]
      (if (or (not= (:request-method request) :post) ; we only validate post
              (nil? validation)                  ; no validation
              (empty? (validate-fields body-coerced validation)))
        (handler request')
        (do
          (prn "failed validation")
          {:status 400
           :headers {"Content-Type" "application/json"
                     "Access-Control-Allow-Origin" "*"}
           :body {:error "Request body does not conform to the expected schema."
                  :problems "FIXME"}})))))


(defn app
  "reitit with format negotiation and input & output coercion"
  [queryfn config session-store problems]
  ;; we define a middleware that includes our query builder
  (let [wrap-query-builder (fn [handler]
                             (fn [request]
                               (handler (assoc request :queryfn queryfn))))
        wrap-problems (fn [handler]
                        (fn [request]
                          (handler (assoc request 
                                         :problems problems))))
        wrap-config (fn [handler]
                      (fn [request]
                        (handler (assoc request :config config))))]

    (ring/ring-handler
     (ring/router
      [["/" {:handler hl/home-handler}]
       ["/web/"
        {:middleware [[wrap-session {:store session-store}]
                      wrap-anti-forgery]}
        ["login" {:get {:handler hl/login-handler}
                  :post {:handler hl/login-post-handler
                         :validation {:username ::string
                                      :password ::string}}}]
        ["logout" {:get {:handler hl/logout-handler}}]

        ["public/*path" {:get {:middleware [wrap-content-type
                                            [wrap-resource ""]]
                               :handler hl/not-found-handler}}]

        ["secure/"
         {:middleware [wrap-auth
                       wrap-problems
                       wrap-config]}
         ["runs/"
          ["display" {:get {:handler hl/display-runs-page}}]
          ["delete-run" {:post {:handler hl/delete-run-handler
                                :validation {:run_id ::vector-of-strings}}}]
          ["create-run" {:post {:handler hl/create-run-handler
                                :validation {:exam-set ::exam-sets}}}]]]]

       ;; API
       ["/api/"
        {:middleware [output-to-json
                      wrap-problems
                      wrap-config]}
        ["is-pending-run"
         {:post {:handler api/pending-run?
                 :validation {:run-id ::uuid}}}]

        ["start-run"
         {:post {:handler api/start-run
                 :validation {:client-id ::string
                              :subset ::anything
                              :existing-run-id ::anything}}}]

        ["test-function"
         {:post {:handler api/test-function
                 :middleware [api/wrap-check-run
                              api/wrap-check-attempt
                              api/wrap-validate-args]
                 :validation {:run-id ::uuid
                              :attempt-id ::uuid
                              :args ::anything-collection}

                 }}]

        ["next-verification"
         {:post {:handler api/next-verification
                 :middleware [api/wrap-check-run
                              api/wrap-check-attempt
                              api/wrap-record-started]
                 :validation {:run-id ::uuid
                              :attempt-id ::uuid}}}]

        ["attempt-verification"
         {:post {:handler api/attempt-verification
                 :middleware [api/wrap-check-run
                              api/wrap-check-attempt
                              api/wrap-record-started]
                 :validation {:run-id ::uuid
                              :attempt-id ::uuid
                              :prediction ::anything}}}]

        ["complete-run"
         {:post {:handler api/complete-run
                 :middleware [api/wrap-check-run]
                 :validation {:run-id ::uuid}}}]]]

      ;; router data affecting all routes
      {:data {:coercion   reitit.coercion.spec/coercion
              :muuntaja   m/instance
              :middleware [;; log-path-middleware
                           parameters/parameters-middleware
                           [wrap-json-body {:keywords? true}]
                           wrap-validate-body
                           muuntaja/format-response-middleware
                           rrc/coerce-response-middleware
                           logger/wrap-with-logger
                           wrap-query-builder
                           ]}

       :reitit.ring/default-options-endpoint
       {:no-doc true
        :handler (fn [request]
                  {:status 200, :body "", :headers {"Allow" "POST,OPTIONS"
                                                    "Access-Control-Allow-Origin" "*"
                                                    "Access-Control-Allow-Headers" "*"}})}})
     hl/not-found-handler)))
