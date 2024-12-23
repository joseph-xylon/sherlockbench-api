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
  (post-api "test-function" {:run-id "09028003-98aa-4e3f-b865-3426b300b629"
                             :attempt-id "95333c77-e1e9-4cd2-a8a8-7024e4f547b4"
                             :args [9 4]})
  )
