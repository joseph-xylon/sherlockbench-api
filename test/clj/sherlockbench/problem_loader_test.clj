(ns sherlockbench.problem-loader-test
  (:require [clojure.test :refer :all]
            [sherlockbench.problem-loader :refer :all]
            [clojure.pprint :refer [pprint]]))

(def problems
        [{:namespace 'apple
          :name- "add-or-multiply"
          :tags #{:all :foo}}
         {:namespace 'nana
          :name- "add-or-multiply"
          :tags #{:all :foo}}
         {:namespace 'apple
          :name- "subtract-two-later"
          :tags #{:all :bar}}
         {:namespace 'apple
          :name- "difference-of-closest-pair"
          :tags #{:all}}
         {:namespace 'apple
          :name- "subtract-and-divide"
          :tags #{:all}}
         {:namespace 'nana
          :name- "conditional-concat"
          :tags #{:all :foo :bar}}
         {:namespace 'nana
          :name- "first-last-with-middle"
          :tags #{:all :bar}}
         {:namespace 'nana
          :name- "longest-common-substring"
          :tags #{:all :foo}}
         {:namespace 'nana
          :name- "conditional-double"
          :tags #{:all}}
         {:namespace 'pear
          :name- "set-heading"
          :tags #{:all :foo}}
         {:namespace 'pear
          :name- "count-pairwise-even-sums"
          :tags #{:all :pie}}
         {:namespace 'pear
          :name- "count-pairwise-even-sums"
          :tags #{:all :pie}}
         ])

(deftest test-assemble-tagged-set
  (is (= 5 (count (assemble-tagged-set :foo problems))))
  (is (= 3 (count (assemble-tagged-set :bar problems)))))

(deftest test-filter-by-name
  (testing "filtering by single name"
    (let [result (filter-by-name problems ['("apple" "add-or-multiply")])]
      (is (= 1 (count result)))
      (is (= "add-or-multiply" (:name- (first result))))))
  
  (testing "filtering by multiple names"
    (let [result (filter-by-name problems ['("nana" "add-or-multiply") '("pear" "set-heading")])]
      (is (= 2 (count result)))
      (is (= #{"add-or-multiply" "set-heading"} 
             (set (map :name- result))))))
  
  (testing "filtering with non-existent names"
    (is (empty? (filter-by-name problems ['("nana" "non-existent-name")])))
    (let [result (filter-by-name problems ['("apple" "add-or-multiply") '("apple" "non-existent-name")])]
      (is (= 1 (count result)))
      (is (= "add-or-multiply" (:name- (first result))))))

  (testing "test rejection of duplicates"
    (is (= 1 (count (filter-by-name problems ['("pear" "count-pairwise-even-sums")]))))))

(deftest test-filter-by-tags
  (testing "filtering by single tag"
    (let [result (filter-by-tags problems [:foo])]
      (is (= 5 (count result)))
      (is (every? #(contains? (:tags %) :foo) result))))
  
  (testing "filtering by multiple tags"
    (let [result (filter-by-tags problems [:foo :bar])]
      (is (= 7 (count result)))
      (is (every? #(or (contains? (:tags %) :foo) 
                       (contains? (:tags %) :bar)) result))))
  
  (testing "filtering with non-existent tag"
    (is (empty? (filter-by-tags problems [:non-existent-tag])))
    (let [result (filter-by-tags problems [:foo :non-existent-tag])]
      (is (= 5 (count result)))
      (is (every? #(contains? (:tags %) :foo) result))))

  (testing "reject duplicates"
    (is (= 1 (count (filter-by-tags problems [:pie]))))))

(deftest test-string-to-tag
  (is (= :custom-myfavourites (string-to-tag "My Favourites")))
  (is (= :custom-easy3 (string-to-tag "E'asy3"))))

(comment
  (test-assemble-tagged-set)
  (test-filter-by-name)
  (test-filter-by-tags)
  (test-string-to-tag)
  (string-to-tag "My Favorites")
  (pprint (filter-by-name problems ['("apple" "add-or-multiply")]))
  )
