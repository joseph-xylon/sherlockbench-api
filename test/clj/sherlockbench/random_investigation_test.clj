(ns sherlockbench.random-investigation-test
  (:require [clojure.test :refer :all]
            [sherlockbench.random-investigation :refer :all]
            [clojure.string :as str]))

(deftest test-generate-random-value
  (testing "integer type generates valid integers"
    (let [result (#'sherlockbench.random-investigation/generate-random-value "integer")]
      (is (integer? result))
      (is (>= result 0))
      (is (< result 100))))
  
  (testing "string type generates valid dictionary words"
    (let [result (#'sherlockbench.random-investigation/generate-random-value "string")]
      (is (string? result))
      (is (contains? (set dictionary-words) result))))
  
  (testing "boolean type generates valid booleans"
    (let [result (#'sherlockbench.random-investigation/generate-random-value "boolean")]
      (is (boolean? result))))
  
  (testing "unknown type throws exception"
    (is (thrown? IllegalArgumentException 
                 (#'sherlockbench.random-investigation/generate-random-value "unknown")))))

(deftest test-quote-if-string
  (testing "strings are wrapped in quotes"
    (is (= "\"hello\"" (quote-if-string "hello")))
    (is (= "\"\"" (quote-if-string ""))))
  
  (testing "true becomes True"
    (is (= "True" (quote-if-string true))))
  
  (testing "false becomes False"
    (is (= "False" (quote-if-string false))))
  
  (testing "other values remain unchanged"
    (is (= 42 (quote-if-string 42)))
    (is (= 3.14 (quote-if-string 3.14)))
    (is (= nil (quote-if-string nil)))))

(deftest test-rfn
  (testing "first call with empty accumulator"
    (let [result (rfn + "" [1 2])]
      (is (= "(1, 2) → 3" result))))
  
  (testing "subsequent calls append with newline"
    (let [acc "(1, 2) → 3"
          result (rfn + acc [3 4])]
      (is (= "(1, 2) → 3\n(3, 4) → 7" result))))
  
  (testing "string arguments are quoted"
    (let [result (rfn str "" ["hello" "world"])]
      (is (= "(\"hello\", \"world\") → \"helloworld\"" result))))
  
  (testing "boolean arguments use True/False"
    (let [result (rfn (fn [x y] (and x y)) "" [true false])]
      (is (= "(True, False) → False" result)))))

(deftest test-random-fn-inputs
  (testing "generates correct number of lines"
    (let [result (random-fn-inputs ["integer" "integer"] + 3)
          lines (str/split-lines result)]
      (is (= 3 (count lines)))))
  
  (testing "each line has correct format"
    (let [result (random-fn-inputs ["integer" "integer"] + 2)
          lines (str/split-lines result)]
      (doseq [line lines]
        (is (re-matches #"\(\d+, \d+\) → \d+" line)))))
  
  (testing "string inputs are quoted in output"
    (let [result (random-fn-inputs ["string"] identity 1)]
      (is (re-matches #"\(\"[^\"]+\"\) → \"[^\"]+\"" result))))
  
  (testing "boolean inputs use True/False format"
    (let [result (random-fn-inputs ["boolean"] not 1)]
      (is (or (= "(True) → False" result)
              (= "(False) → True" result)))))
  
  (testing "empty spec with zero calls returns empty string"
    (let [result (random-fn-inputs [] (fn []) 0)]
      (is (= "" result))))
  
  (testing "single call with no args"
    (let [result (random-fn-inputs [] (constantly 42) 1)]
      (is (= "() → 42" result)))))

(deftest test-edge-cases
  (testing "zero function calls"
    (let [result (random-fn-inputs ["integer"] identity 0)]
      (is (= "" result))))
  
  (testing "function that throws exception"
    (is (thrown? Exception
                 (random-fn-inputs ["integer"] 
                                   (fn [x] (throw (Exception. "test error"))) 
                                   1))))
  
  (testing "complex function with mixed types"
    (let [complex-fn (fn [s i b] (str s "-" i "-" b))
          result (random-fn-inputs ["string" "integer" "boolean"] complex-fn 1)
          pattern #"\(\"[^\"]+\", \d+, (True|False)\) → \"[^\"]+\""]
      (is (re-matches pattern result)))))

(comment
  (test-generate-random-value)
  (test-quote-if-string)
  (test-rfn)
  (test-random-fn-inputs)
  (test-edge-cases)
  )
