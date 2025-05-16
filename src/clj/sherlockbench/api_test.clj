(ns sherlockbench.api-test
  "Playground for me to test the API"
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]))

(def api-url "http://0.0.0.0:3000/api/")
;; (def api-url "http://api.sherlockbench.com/api/")

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
  (get-api "problem-sets") 
  (post-api "is-pending-run" {"run-id" "4681dd64-c1df-4511-9bad-d4127bd9aa1e"})
  (post-api "start-run" {"client-id" "api_test.clj"
                         "problem-set" "sherlockbench.sample-problems/easy3"})
  (post-api "start-run" {"client-id" "api_test.clj"
                         "existing-run-id" ""
                         })
  (post-api "test-function" {:run-id "72f29a88-afa0-413a-b7b6-658177a2c757"
                             :attempt-id "07158652-9a4c-41bd-9a81-e054b328c659"
                             :args [9 4 3]})
  (post-api "next-verification" {:run-id "eb55663c-ac29-46c5-8474-857fbbdf4cf0"
                                 :attempt-id "21cf991e-7ae8-4061-ab64-9602fde51815"})
  (post-api "attempt-verification" {:run-id "8918d432-e80c-491e-b5a0-a40006fa1ff7"
                                    :attempt-id "01071213-ba72-4be5-9616-673ccab24428"
                                    :prediction 5})
  (post-api "developer/problem-names" {:run-id "72f29a88-afa0-413a-b7b6-658177a2c757"})
  (post-api "complete-run" {:run-id "8918d432-e80c-491e-b5a0-a40006fa1ff7"})
  
  )
