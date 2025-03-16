(ns sherlockbench.problem-loader-test
  (:require [clojure.test :refer :all]
            [sherlockbench.problem-loader :refer :all]))

(def problems
        [{:name- "add-or-multiply"
          :tags #{:all :foo}}
         {:name- "subtract-two-later"
          :tags #{:all :bar}}
         {:name- "difference-of-closest-pair"
          :tags #{:all}}
         {:name- "subtract-and-divide"
          :tags #{:all}}
         {:name- "conditional-concat"
          :tags #{:all :foo :bar}}
         {:name- "first-last-with-middle"
          :tags #{:all :bar}}
         {:name- "longest-common-substring"
          :tags #{:all :foo}}
         {:name- "conditional-double"
          :tags #{:all}}
         {:name- "set-heading"
          :tags #{:all :foo}}
         {:name- "count-pairwise-even-sums"
          :tags #{:all}}
         ])

(deftest test-assemble-tagged-set
  (is (= 4 (count (assemble-tagged-set :foo problems))))
  (is (= 3 (count (assemble-tagged-set :bar problems)))))

(deftest test-filter-by-name
  (testing "filtering by single name"
    (let [result (filter-by-name problems ["add-or-multiply"])]
      (is (= 1 (count result)))
      (is (= "add-or-multiply" (:name- (first result))))))
  
  (testing "filtering by multiple names"
    (let [result (filter-by-name problems ["add-or-multiply" "set-heading"])]
      (is (= 2 (count result)))
      (is (= #{"add-or-multiply" "set-heading"} 
             (set (map :name- result))))))
  
  (testing "filtering with non-existent names"
    (is (empty? (filter-by-name problems ["non-existent-name"])))
    (let [result (filter-by-name problems ["add-or-multiply" "non-existent-name"])]
      (is (= 1 (count result)))
      (is (= "add-or-multiply" (:name- (first result)))))))

(deftest test-filter-by-tags
  (testing "filtering by single tag"
    (let [result (filter-by-tags problems [:foo])]
      (is (= 4 (count result)))
      (is (every? #(contains? (:tags %) :foo) result))))
  
  (testing "filtering by multiple tags"
    (let [result (filter-by-tags problems [:foo :bar])]
      (is (= 6 (count result)))
      (is (every? #(or (contains? (:tags %) :foo) 
                       (contains? (:tags %) :bar)) result))))
  
  (testing "filtering with non-existent tag"
    (is (empty? (filter-by-tags problems [:non-existent-tag])))
    (let [result (filter-by-tags problems [:foo :non-existent-tag])]
      (is (= 4 (count result)))
      (is (every? #(contains? (:tags %) :foo) result)))))

(deftest test-string-to-tag
  (is (= :custom-myfavourites (string-to-tag "My Favourites")))
  (is (= :custom-easy3 (string-to-tag "E'asy3"))))

(comment
  (test-assemble-tagged-set)
  (test-filter-by-name)
  (test-filter-by-tags)
  (test-string-to-tag)
  (string-to-tag "My Favorites")
  )
