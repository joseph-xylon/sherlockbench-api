(ns sherlockbench.random-test
  (:require [clojure.test :refer :all]
            [sherlockbench.random :as rnd]))

(deftest make-rng-test
  (testing "nil seed yields no rng"
    (is (nil? (rnd/make-rng nil))))
  (testing "a seed yields a java.util.Random"
    (is (instance? java.util.Random (rnd/make-rng 42)))))

(deftest determinism-test
  (testing "same seed produces the same sequence"
    (let [draw (fn [seed] (let [rng (rnd/make-rng seed)]
                            (repeatedly 10 #(rnd/rand-int rng 100))))]
      (is (= (draw 42) (draw 42)))))

  (testing "different seeds (very probably) differ"
    (let [draw (fn [seed] (let [rng (rnd/make-rng seed)]
                            (repeatedly 10 #(rnd/rand-int rng 100))))]
      (is (not= (draw 1) (draw 2)))))

  (testing "rand-int stays within bounds"
    (let [rng (rnd/make-rng 7)]
      (is (every? #(< -1 % 100) (repeatedly 100 #(rnd/rand-int rng 100)))))))

(deftest fallback-test
  (testing "nil rng falls back to clojure.core and stays in bounds"
    (is (every? #(< -1 % 10) (repeatedly 100 #(rnd/rand-int nil 10))))
    (is (<= 0.0 (rnd/rand nil) 1.0))
    (is (contains? #{:a :b :c} (rnd/rand-nth nil [:a :b :c])))))

(deftest rand-nth-deterministic-test
  (testing "seeded rand-nth is reproducible"
    (let [pick (fn [seed] (let [rng (rnd/make-rng seed)]
                            (repeatedly 10 #(rnd/rand-nth rng [:a :b :c :d :e]))))]
      (is (= (pick 99) (pick 99))))))

(deftest rand-string-test
  (testing "correct length and alphanumeric"
    (is (= 12 (count (rnd/rand-string (rnd/make-rng 1) 12))))
    (is (re-matches #"[A-Za-z0-9]+" (rnd/rand-string (rnd/make-rng 1) 12))))

  (testing "seeded strings are reproducible"
    (is (= (rnd/rand-string (rnd/make-rng 5) 16)
           (rnd/rand-string (rnd/make-rng 5) 16))))

  (testing "different seeds (very probably) differ"
    (is (not= (rnd/rand-string (rnd/make-rng 5) 16)
              (rnd/rand-string (rnd/make-rng 6) 16))))

  (testing "nil rng still produces a valid token"
    (is (= 8 (count (rnd/rand-string nil 8))))))
