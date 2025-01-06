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
  (get-api "start-run?subset=easy3")
  (post-api "test-function" {:run-id "d8e46e98-12bf-42ac-8f7a-a89eff4ba73f"
                             :attempt-id "11a5077c-758d-43c8-87eb-532d353d7e84"
                             :args [9]})
  (post-api "next-verification" {:run-id "d8e46e98-12bf-42ac-8f7a-a89eff4ba73f"
                                 :attempt-id "11a5077c-758d-43c8-87eb-532d353d7e84"})
  (post-api "attempt-verification" {:run-id "d8e46e98-12bf-42ac-8f7a-a89eff4ba73f"
                                    :attempt-id "11a5077c-758d-43c8-87eb-532d353d7e84"
                                    :prediction 5})
  (post-api "complete-run" {:run-id "d8e46e98-12bf-42ac-8f7a-a89eff4ba73f"})
  )
