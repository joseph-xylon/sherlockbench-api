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
            [sherlockbench.handlers :as hl]
            [sherlockbench.api :as api]
            [sherlockbench.debug-middlewares :refer [wrap-debug-reqmap whenwrap]]
            [ring.logger :as logger]
            [clojure.data.json :as json]))

(s/def ::id int?)
(s/def ::string string?)
(s/def ::anything any?)
(s/def ::anything-collection (s/coll-of any?))

(defn valid-uuid? [uuid]
  (try
    (java.util.UUID/fromString uuid)
    true
    (catch IllegalArgumentException _ false)))

(s/def ::uuid (s/and string? valid-uuid?))

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
         :headers {"Location" (str "/login?redirect=" redirect-url)}
         :body ""}))))

(defn output-to-json [handler]
  (fn [request]
    (update (handler request) :body json/write-str)))

(defn wrap-validate-body [handler]
  (fn [request]
    (let [validation (get-in request [:reitit.core/match :data :post :validation])
          body (:body request)]
      (if (or (nil? validation)
              (s/valid? (s/keys :req-un (keys validation)) body))
        (handler request)
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body {:error "Request body does not conform to the expected schema."
                :problems (s/explain-data (s/keys :req-un (keys validation)) body)}}))))

(defn load-problems
  "Safely load the problems from a namespace"
  [ns]
  (try
    (require ns)
    (let [problems-symbol (ns-resolve ns 'problems)]
      (if problems-symbol
        (do (prn ns)
          @problems-symbol)
        (do
          (println "Namespace" ns "does not define 'problems'")
          [])))
    (catch Exception e
      (println "Failed to load problems from" ns ":" (.getMessage e))
      [])))

(defn aggregate-problems
  "Combine all problems from the namespaces"
  [namespaces]
  (apply concat (map load-problems (conj namespaces 'sherlockbench.problems))))

(defn app
  "reitit with format negotiation and input & output coercion"
  [queryfn config]
  ;; we define a middleware that includes our query builder
  (let [wrap-query-builder (fn [handler]
                             (fn [request]
                               (handler (assoc request :queryfn queryfn))))
        problems (aggregate-problems (:extra-namespaces config))
        wrap-problems (fn [handler]
                        (fn [request]
                          (handler (assoc request :problems problems))))
        session-store (memory/memory-store)]
    

    (ring/ring-handler
     (ring/router
      [["/" {:handler hl/home-handler}]
       ["/web/"
        {:middleware [[wrap-session {:store session-store}]
                      wrap-anti-forgery]}
        ["login" {:get {:handler hl/login-handler}
                  :post {:handler hl/login-post-handler
                         :parameters {:form {:username ::string
                                             :password ::string}}}}]
        ["logout" {:get {:handler hl/logout-handler}}]

        ["public/*path" {:get {:middleware [wrap-content-type
                                            [wrap-resource ""]]
                               :handler hl/not-found-handler}}]]

       ;; API
       ["/api/"
        {:middleware [output-to-json
                      wrap-problems]}
        ["start-run"
         {:get {:handler api/start-anonymous-run
                :parameters {:path {:subset ::string}}}}]

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
              :middleware [parameters/parameters-middleware
                           [wrap-json-body {:keywords? true}]
                           wrap-validate-body
                           muuntaja/format-response-middleware
                           rrc/coerce-response-middleware
                           logger/wrap-with-logger
                           wrap-query-builder]}})
     hl/not-found-handler)))
