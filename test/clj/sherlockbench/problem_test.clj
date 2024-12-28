(ns sherlockbench.problem-test
  (:require [clojure.test :refer :all]
            [sherlockbench.problems :refer [problems]]))

(defn find-map-by-key-value [key value coll]
  (some #(when (= (key %) value) %) coll))

(defn eval-fn [fn-name & args]
  (apply (:function (find-map-by-key-value :name- fn-name problems)) args))

(deftest test-add-and-subtract
  (is (= 6 (eval-fn "add & subtract" 5 4 3)))
  (is (= 13 (eval-fn "add & subtract" 10 5 2)))
  (is (= 2 (eval-fn "add & subtract" 7 2 7))))

(deftest test-is-prime
  ;; The :verifications in problems were [2] [7] [4] [1] [13] [25]
  ;; Checking each against the prime-check function
  (is (= true  (eval-fn "is prime" 2)))
  (is (= true  (eval-fn "is prime" 7)))
  (is (= false (eval-fn "is prime" 4)))
  (is (= false (eval-fn "is prime" 1)))
  (is (= true  (eval-fn "is prime" 13)))
  (is (= false (eval-fn "is prime" 25))))

(deftest test-modulus-3-to-fruit
  ;; The function returns "banana" (for n mod 3 = 0),
  ;; "apple" (for n mod 3 = 1), "orange" (for n mod 3 = 2)
  (is (= "orange" (eval-fn "modulus 3 to fruit" 17)))  ; 17 mod 3 = 2
  (is (= "banana" (eval-fn "modulus 3 to fruit" 12)))  ; 12 mod 3 = 0
  (is (= "apple"  (eval-fn "modulus 3 to fruit" 10)))) ; 10 mod 3 = 1

(deftest test-ignore-one
  ;; The function is (fn [a b c] (* a c))
  ;; The second argument is effectively "ignored."
  (is (= 27 (eval-fn "ignore one" 3 2 9))) ; 3*9 = 27
  (is (= 8  (eval-fn "ignore one" 4 5 2))) ; 4*2 = 8
  (is (= 0  (eval-fn "ignore one" 0 9 3)))) ; 0*3 = 0

(deftest test-count-vowels
  ;; The function counts vowels in a string
  (is (= 2 (eval-fn "count vowels" "vector")))   ; e, o => 2
  (is (= 3 (eval-fn "count vowels" "ocean")))    ; o, e, a => 3
  (is (= 1 (eval-fn "count vowels" "strength")))) ; e => 1

(deftest test-add-with-toggle-sign
  ;; The function sums a and b, then if c is true it negates the sum,
  ;; otherwise leaves it as is.
  (is (= 9  (eval-fn "add with toggle sign" 4 5 false))) ; 4+5=9, c=false => 9
  (is (= 10 (eval-fn "add with toggle sign" 7 3 false))) ; 7+3=10, c=false => 10
  (is (= -8 (eval-fn "add with toggle sign" 10 -2 true)))) ; 8 => -8 if c=true

(deftest test-concat-nth-char
  ;; Concatenate string a with the nth character of string b
  (is (= "hellor"    (eval-fn "concat nth char" "hello" "world" 2))) 
  ;; "hello" + (nth "world" 2 => 'r')
  (is (= "abcg"      (eval-fn "concat nth char" "abc" "defg" 3)))
  ;; "abc" + (nth "defg" 3 => 'g')
  (is (= "clojurer"  (eval-fn "concat nth char" "clojure" "program" 4))))
  ;; "clojure" + (nth "program" 4 => 'r')

(deftest test-times-three-and-round
  ;; The function is (* 5 (Math/round (/ (* 3 n) 5.0)))
  ;; For n=4 => (3*4)=12 => (/12 5)=2.4 => round=2 => (* 5)=10
  ;; For n=7 => (3*7)=21 => (/21 5)=4.2 => round=4 => (* 5)=20
  ;; For n=8 => (3*8)=24 => (/24 5)=4.8 => round=5 => (* 5)=25
  (is (= 10 (eval-fn "times three and round" 4)))
  (is (= 20 (eval-fn "times three and round" 7)))
  (is (= 25 (eval-fn "times three and round" 8))))

(deftest test-filter-consonants-and-vowels
  ;; The function moves vowels (in order) to the front,
  ;; then consonants (in order).
  ;; e.g. "silent smart" => vowels: i,e,a => "iea"
  ;;                          consonants: s,l,n,t, ,s,m,r,t => "slnt smart"
  ;;                        => "ieaslnt smart"
  (is (= "ieaslnt smrt" (eval-fn "filter consonants and vowels" "silent smart")))
  (is (= "aiorpd cld"    (eval-fn "filter consonants and vowels" "rapid cold")))
  (is (= "uiaqck ct"     (eval-fn "filter consonants and vowels" "quick cat"))))

(deftest test-interleave-characters
  ;; Interleaves characters: 
  ;;   "abc" and "123" => "a1b2c3"
  ;; leftover characters get appended at the end.
  (is (= "a1b2c3"      (eval-fn "interleave characters" "abc" "123")))
  (is (= "hweolrllod"  (eval-fn "interleave characters" "hello" "world")))
  (is (= "c1l2o3jure"  (eval-fn "interleave characters" "clojure" "123")))
  (is (= "slhoonrgter" (eval-fn "interleave characters" "short" "longer")))
  (is (= "aBCD"        (eval-fn "interleave characters" "a" "BCD")))
  (is (= "empty"       (eval-fn "interleave characters" "" "empty")))
  (is (= "nonempty"    (eval-fn "interleave characters" "nonempty" ""))))
