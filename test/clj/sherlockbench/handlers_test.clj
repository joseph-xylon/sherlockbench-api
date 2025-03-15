(ns sherlockbench.handlers-test
  (:require [clojure.test :refer :all]
            [sherlockbench.handlers :as handlers]))

(deftest test-group-problem-sets
  (testing "grouping problem sets by namespace and type"
    (let [problem-sets {;; Namespaced sets (standard)
                        :problems/easy {:name "Easy Problems" :description "Beginner friendly problems"}
                        :problems/medium {:name "Medium Problems" :description "Intermediate level problems"}
                        :problems/hard {:name "Hard Problems" :description "Advanced problems"}
                        
                        ;; Sets with slashes in the name (no namespace)
                        :algo/sorting {:name "Sorting Algorithms" :description "Sort implementation problems"}
                        :ds/trees {:name "Tree Problems" :description "Binary tree problems"}
                        
                        ;; Sets with "all" in the name
                        :problems/all {:name "All Problems" :description "Complete set of problems"}
                        
                        ;; Custom sets
                        :custom/set1 {:name "Custom Set 1" :description "First custom set"}
                        :custom/set2 {:name "Custom Set 2" :description "Second custom set"}
                        
                        ;; Unmatched set (should be filtered out)
                        :special-set {:name "Special Set" :description "Should not be included"}}
          
          result (handlers/group-problem-sets problem-sets)]
      
      ;; Check namespaced groups structure
      (is (map? (:namespaced result)) "Namespaced result should be a map")
      (is (= #{"problems" "algo" "ds"} (set (keys (:namespaced result)))) "Should have correct namespace keys")
      
      ;; Check problems namespace contents
      (let [problems-group (get-in result [:namespaced "problems"])]
        (is (= 4 (count problems-group)) "Problems namespace should have 4 entries")
        (is (some #(= :problems/all (first %)) problems-group) "Should include the 'all' entry")
        (is (some #(= :problems/easy (first %)) problems-group) "Should include the 'easy' entry"))
      
      ;; Check custom sets
      (is (seq (:custom result)) "Custom sets should not be empty")
      (is (= 2 (count (:custom result))) "Should have 2 custom sets")
      (is (every? #(= (namespace (first %)) "custom") (:custom result)) "All custom sets should have 'custom' namespace")
      
      ;; Check that special set is filtered out
      (is (not-any? #(= :special-set (first %)) 
                   (mapcat second (:namespaced result))) "Special set should be filtered out")
      (is (not-any? #(= :special-set (first %)) 
                   (:custom result)) "Special set should not be in custom sets"))))

(comment
  (test-group-problem-sets)
  )