(ns sherlockbench.handlers-test
  (:require [clojure.test :refer :all]
            [sherlockbench.handlers :as handlers]))

(deftest test-group-problem-sets
  (testing "grouping problem sets by namespace and type"
    (let [problem-sets {:problems/all
                        {:name "Sherlock Demo Problems (All)",
                         :description "Problems tagged as all",
                         :problems {:tags #{:problems/all}},
                         :auto true},
                        :problems/math
                        {:name "Math Problems",
                         :description "Problems tagged as math",
                         :problems {:tags #{:problems/math}},
                         :auto true},
                        :problems/easy3
                        {:name "Easy Demo (3 Problems)",
                         :description "Problems tagged as easy3",
                         :problems {:tags #{:problems/easy3}},
                         :auto true},
                        :custom/easy3
                        {:name "Beginner Problems", :problems {:tags #{:problems/easy3}}},
                        :ng-problems/all
                        {:name "Navigation Problems (All)",
                         :description "Problems tagged as all",
                         :problems {:tags #{:ng-problems/all}},
                         :auto true},
                        :interrobench-problems/representative10
                        {:name "Representative 10",
                         :description "Problems tagged as representative10",
                         :problems {:tags #{:interrobench-problems/representative10}},
                         :auto true},
                        :custom/myfavs
                        {:name "My Favorites",
                         :problems
                         {:tags #{:problems/math :classic/math},
                          :names #{"add & subtract" "is prime"}}},
                        :interrobench-problems/all
                        {:name "Interrobench Problems (All)",
                         :description "Problems tagged as all",
                         :problems {:tags #{:interrobench-problems/all}},
                         :auto true},
                        :custom/string-problems
                        {:name "String Manipulation",
                         :problems {:tags #{:classic/string :problems/string}}},
                        :problems/string
                        {:name "String Manipulation",
                         :description "Problems tagged as string",
                         :problems {:tags #{:problems/string}},
                         :auto true},
                        :classic-problems/all
                        {:name "Classic Problems (All)",
                         :description "Problems tagged as all",
                         :problems {:tags #{:classic-problems/all}},
                         :auto true}}
          
          result (handlers/group-problem-sets problem-sets)]
      
      ;; Check namespaced groups structure
      (is (map? (:namespaced result)) "Namespaced result should be a map")
      (is (= #{"ng-problems" "problems" "classic-problems" "interrobench-problems"} (set (keys (:namespaced result)))) "Should have correct namespace keys")
      
      ;; Check problems namespace contents
      (let [problems-group (get-in result [:namespaced "problems"])]
        (is (= 4 (count problems-group)) "Problems namespace should have 4 entries")
        (is (some #(= :problems/all (first %)) problems-group) "Should include the 'all' entry")
        (is (some #(= :problems/easy3 (first %)) problems-group) "Should include the 'easy' entry"))
      
      ;; Check custom sets
      (is (seq (:custom result)) "Custom sets should not be empty")
      (is (= 3 (count (:custom result))) "Should have 2 custom sets")
      (is (every? #(= (namespace (first %)) "custom") (:custom result)) "All custom sets should have 'custom' namespace")
      
      ;; Check that special set is filtered out
      (is (not-any? #(= :special-set (first %)) 
                   (mapcat second (:namespaced result))) "Special set should be filtered out")
      (is (not-any? #(= :special-set (first %)) 
                   (:custom result)) "Special set should not be in custom sets"))))

(comment
  (test-group-problem-sets)
  )
