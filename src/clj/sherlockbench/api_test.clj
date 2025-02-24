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
  (post-api "is-pending-run" {"run-id" "4681dd64-c1df-4511-9bad-d4127bd9aa1e"})
  (post-api "start-run" {"client-id" "api_test.clj"
                         "subset" "easy3"
                         })
  (post-api "test-function" {:run-id "8918d432-e80c-491e-b5a0-a40006fa1ff7"
                             :attempt-id "01071213-ba72-4be5-9616-673ccab24428"
                             :args [9 4 3]})
  (post-api "next-verification" {:run-id "8918d432-e80c-491e-b5a0-a40006fa1ff7"
                                 :attempt-id "01071213-ba72-4be5-9616-673ccab24428"})
  (post-api "attempt-verification" {:run-id "8918d432-e80c-491e-b5a0-a40006fa1ff7"
                                    :attempt-id "01071213-ba72-4be5-9616-673ccab24428"
                                    :prediction 5})
  (post-api "complete-run" {:run-id "8918d432-e80c-491e-b5a0-a40006fa1ff7"})
  )
