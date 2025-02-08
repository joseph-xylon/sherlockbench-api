(ns sherlockbench.routes-test
  (:require [clojure.test :refer :all]
            [sherlockbench.routes :as routes]))

(deftest test-wrap-validate-body
  (let [handler (fn [_] {:status 200 :headers {} :body "OK"})
        wrapped-handler (routes/wrap-validate-body handler)
        ring-request {:reitit.core/match
                      {:data
                       {:post
                        {:validation {:run-id ::routes/uuid
                                      :foo ::routes/string
                                      :args ::routes/anything-collection}}}}}
        body-valid {:run-id "8ea3979e-6286-4158-8bd1-a8153c28cc1c"
                    :foo "bar"
                    :args ["a" 1]}
        body-missing-val {:run-id "8ea3979e-6286-4158-8bd1-a8153c28cc1c"
                          :args ["a" 1]}
        body-wrong-val {:run-id 1
                        :foo "bar"
                        :args ["a" 1]}]

    (testing "valid body"
      (is (= 200 (:status (wrapped-handler (assoc ring-request :body body-valid))))))
    (testing "missing value"
      (is (= 400 (:status (wrapped-handler (assoc ring-request :body body-missing-val))))))
    (testing "wrong value"
      (is (= 400 (:status (wrapped-handler (assoc ring-request :body body-wrong-val))))))))

(comment
  (test-wrap-validate-body)
  )
