(ns sherlockbench.core-test
  (:require [clojure.test :refer :all]
            [sherlockbench.core :refer :all]))

(deftest test-read-edn-file
  (is (= "John Doe" (:name (read-edn-file "env/test/resources/example.edn")))))

(comment
  (test-read-edn-file)
  )
