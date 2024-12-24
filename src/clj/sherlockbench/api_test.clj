(ns sherlockbench.api-test
  "Playground for me to test the API"
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]))

(def api-url "http://0.0.0.0:3000/api/")

(defn get-api [path]
  (let [response (client/get (str api-url path)
                             {:headers {"Accept" "application/json"}})
        parsed-body (json/read-str (:body response) :key-fn keyword)]

    (println "\nStatus Code:" (:status response))
    (println "Response Body:")
    (pprint parsed-body)))


(defn post-api [path post]
  (let [response (client/post (str api-url path)
                              {:headers {"Content-Type" "application/json"}
                               :body (json/write-str post)})
        parsed-body (json/read-str (:body response) :key-fn keyword)]

    (println "\nStatus Code:" (:status response))
    (println "Response Body:")
    (pprint parsed-body)))

(comment
  (get-api "start-run")
  (post-api "test-function" {:run-id "fe476037-adcb-49e2-9df9-d83fe908b332"
                             :attempt-id "afbc6ae7-aae9-4f1a-a232-d5132a89ff43"
                             :args [6, 4, 6]})
  (post-api "next-verification" {:run-id "fe476037-adcb-49e2-9df9-d83fe908b332"
                                 :attempt-id "afbc6ae7-aae9-4f1a-a232-d5132a89ff43"})
  (post-api "attempt-verification" {:run-id "fe476037-adcb-49e2-9df9-d83fe908b332"
                                    :attempt-id "afbc6ae7-aae9-4f1a-a232-d5132a89ff43"
                                    :prediction 0})
  )
