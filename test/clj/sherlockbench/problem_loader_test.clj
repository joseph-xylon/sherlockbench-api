(ns sherlockbench.problem-loader-test
  (:require [clojure.test :refer :all]
            [sherlockbench.problem-loader :as pl]))

;; Helper functions for testing

(defn create-test-namespace-data [problems tag-names namespace-name]
  {:problems problems
   :namespace 'test.namespace
   :namespace-tag :namespace
   :namespace-name (or namespace-name "Test Namespace")
   :tag-names tag-names})

(defn create-test-problem [name tags]
  {:name- name
   :args ["test"]
   :function (fn [x] x)
   :tags tags})

;; Tests for namespace and tag manipulation functions

(deftest test-namespace->tag
  (testing "Converting namespaces to tags"
    (is (= :problems (pl/namespace->tag 'sherlockbench.problems)))
    (is (= :problems (pl/namespace->tag "sherlockbench.problems")))
    (is (= :core (pl/namespace->tag 'clojure.core)))
    (is (= :test (pl/namespace->tag 'test)))
    (is (= :namespace (pl/namespace->tag 'some.very.nested.namespace)))))

(deftest test-create-ns-all-tag
  (testing "Creating namespace/all tags"
    (is (= :problems/all (pl/create-ns-all-tag :problems)))
    (is (= :math/all (pl/create-ns-all-tag :math)))
    (is (= :core/all (pl/create-ns-all-tag :core)))))

(deftest test-namespace-tag-key
  (testing "Namespacing tag keys"
    (is (= :problems/easy (pl/namespace-tag-key :problems :easy)))
    (is (= :already/namespaced (pl/namespace-tag-key :problems :already/namespaced)))
    (is (= :other/test (pl/namespace-tag-key :other :test)))))

(deftest test-namespace-tag-keys
  (testing "Namespacing all tag keys in a map"
    (let [tag-names {:easy "Easy problems"
                     :math "Math problems"
                     :already/namespaced "Already namespaced"}
          result (pl/namespace-tag-keys :problems tag-names)]
      (is (= {:problems/easy "Easy problems"
              :problems/math "Math problems"
              :already/namespaced "Already namespaced"}
             result)))))

(deftest test-namespace-problem-tags
  (testing "Namespacing tags in a problem"
    (let [problem {:name- "Test Problem"
                   :tags #{:easy :math :already/namespaced}}
          result (pl/namespace-problem-tags problem :problems)]
      (is (contains? (:tags result) :problems))
      (is (contains? (:tags result) :problems/all))
      (is (contains? (:tags result) :problems/easy))
      (is (contains? (:tags result) :problems/math))
      (is (contains? (:tags result) :already/namespaced))
      (is (= 5 (count (:tags result))))))) ; Original 3 + :problems + :problems/all

;; Tests for problem set creation functions

(deftest test-create-namespace-problem-sets
  (testing "Creating problem sets from namespaces"
    (let [namespaces {:problems {:name "Sherlock Problems"
                                 :namespace 'sherlockbench.problems}
                      :advanced {:name "Advanced Problems"
                                :namespace 'sherlockbench.advanced-problems}}
          result (pl/create-namespace-problem-sets namespaces)]
      (is (= 2 (count result)))
      (is (contains? result :problems/all))
      (is (contains? result :advanced/all))
      (is (= "Sherlock Problems (All)" (get-in result [:problems/all :name])))
      (is (= "All problems from Sherlock Problems" (get-in result [:problems/all :description])))
      (is (= true (get-in result [:problems/all :auto]))))))

(deftest test-create-tag-based-problem-sets
  (testing "Creating problem sets from tags"
    (let [tag-names {:problems/easy "Easy Problems"
                     :problems/math "Math Problems"}
          result (pl/create-tag-based-problem-sets tag-names)]
      (is (= 2 (count result)))
      (is (contains? result :problems/easy))
      (is (contains? result :problems/math))
      (is (= "Easy Problems" (get-in result [:problems/easy :name])))
      (is (= "Problems tagged as easy" (get-in result [:problems/easy :description])))
      (is (= #{:problems/easy} (get-in result [:problems/easy :problems :tags])))
      (is (= true (get-in result [:problems/easy :auto]))))))

;; Tests for namespace data extraction

(deftest test-extract-namespace-data
  (testing "Extracting namespace data from loaded namespace"
    (let [ns-data {:namespace 'test.namespace
                   :namespace-tag :namespace
                   :namespace-name "Test Namespace"}
          result (pl/extract-namespace-data {} ns-data)]
      (is (= {:namespace {:name "Test Namespace"
                          :namespace 'test.namespace}}
             result)))))

(deftest test-extract-tag-names
  (testing "Extracting tag names from loaded namespace"
    (let [ns-data {:namespace-tag :namespace
                   :namespace-name "Test Namespace"
                   :tag-names {:easy "Easy Tests"}}
          result (pl/extract-tag-names {} ns-data)]
      (is (= {:namespace/all "Test Namespace (All)"
              :easy "Easy Tests"}
             result)))))

;; Integration test for combined function
;; This doesn't test the actual functionality since it requires loading 
;; real namespaces, but it tests the structure of the result

(deftest test-aggregate-problems-with-sets-structure
  (testing "Structure of result from combined aggregation and problem sets function"
    (let [custom-problem-sets {:custom {:name "Custom Set"
                                        :description "Custom problem set"}}
          result (pl/aggregate-problems-with-sets [] custom-problem-sets)] ; Empty list to avoid actual loading
      (is (map? result))
      (is (contains? result :problems))
      (is (contains? result :namespaces))
      (is (contains? result :tag-names))
      (is (contains? result :problem-sets))
      (is (sequential? (:problems result)))
      (is (map? (:namespaces result)))
      (is (map? (:tag-names result)))
      (is (map? (:problem-sets result)))
      (is (contains? (:problem-sets result) :custom)))))

(comment
  (test-namespace->tag)
  (test-create-ns-all-tag)
  (test-namespace-tag-key)
  (test-namespace-tag-keys)
  (test-namespace-problem-tags)
  (test-create-namespace-problem-sets)
  (test-create-tag-based-problem-sets)
  (test-extract-namespace-data)
  (test-extract-tag-names)
  (test-aggregate-problems-with-sets-structure)
  )
