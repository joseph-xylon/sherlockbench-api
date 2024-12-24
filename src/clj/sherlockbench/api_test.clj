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
  (post-api "test-function" {:run-id "0f5014bc-be55-452f-a75e-4dbda88102e1"
                             :attempt-id "4ada1e8f-4881-42ab-9a6d-ea6fe1728800"
                             :args [9, 2]})
  (post-api "next-verification" {:run-id "0f5014bc-be55-452f-a75e-4dbda88102e1"
                                 :attempt-id "4ada1e8f-4881-42ab-9a6d-ea6fe1728800"})
  (post-api "attempt-verification" {:run-id "0f5014bc-be55-452f-a75e-4dbda88102e1"
                                    :attempt-id "4ada1e8f-4881-42ab-9a6d-ea6fe1728800"
                                    :prediction 5})
  (post-api "complete-run" {:run-id "0f5014bc-be55-452f-a75e-4dbda88102e1"})
  )
