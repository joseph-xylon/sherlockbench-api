(ns sherlockbench.problems)

;; Namespace metadata
(def namespace-name "Sherlock Demo Problems")

;; Map of tag names - display names for the UI
(def tag-names
  {:problems/easy3 "Easy Demo (3 Problems)"
   :problems/math "Math Problems"
   :problems/string "String Manipulation"})

;; The list of functions which the LLM will interrogate
(def problems
  [
   {:name- "add & subtract"
    :args ["integer" "integer" "integer"]
    :function (fn [a b c]
                (- (+ a b) c))
    :verifications [[1, 2, 3], [10, 5, 2], [7, 2, 7]]
    :output-type "integer"
    :test-limit 20
    :tags #{:easy3 :math}}

   {:name- "is prime"
    :args ["integer"]
    :function (fn [n]
                (cond
                  (<= n 1) false ; Numbers less than or equal to 1 are not prime
                  (= n 2) true   ; 2 is the only even prime number
                  (even? n) false       ; Other even numbers are not prime
                  :else
                  (let [sqrt-n (Math/sqrt n)]
                    (not-any? #(zero? (mod n %))
                              (range 3 (inc (int sqrt-n)) 2)))))
    :verifications [[23] [4] [13] [25]]
    :output-type "boolean"
    :test-limit 20
    :tags #{:easy3 :math}}

   {:name- "modulus 3 to fruit"
    :args ["integer"]
    :function (fn [n]
                (case (mod n 3)
                  0 "banana"
                  1 "apple"
                  2 "orange"))
    :verifications [[12], [-1], [6]]
    :output-type "string"
    :test-limit 20
    :tags #{:easy3 :math}}

   {:name- "ignore one"
    :args ["integer" "integer" "integer"]
    :function (fn [a b c]
                (* a c))
    :verifications [[3, 2, 9], [4, 5, 2], [0, 9, 3]]
    :output-type "integer"
    :test-limit 20
    :tags #{:math}}

   {:name- "count vowels"
    :args ["string"]
    :function (fn [s]
                (count (filter #(#{\a \e \i \o \u \A \E \I \O \U} %) s)))
    :verifications [["vector"], ["ocean"], ["strength"]]
    :output-type "integer"
    :test-limit 20
    :tags #{:string}}

   {:name- "add with toggle sign"
    :args ["integer" "integer" "boolean"]
    :function (fn [a b c]
                (let [sum (+ a b)]
                  (if c
                    (- sum)
                    sum)))
    :verifications [[4, 5, false],[7, 3, false], [10, -2, true]]
    :output-type "integer"
    :test-limit 20
    :tags #{:math}}

   {:name- "concat nth char"
    :args ["string" "string" "integer"]
    :function (fn [a b c]
                (str a (nth b c)))
    :verifications [["hello", "world", 2], ["abc", "defg", 3], ["clojure", "program", 4]]
    :output-type "string"
    :test-limit 20
    :tags #{:string}}

   {:name- "times three and round"
    :args ["integer"]
    :function (fn [n]
                (* 5 (Math/round (/ (* 3 n) 5.0))))
    :verifications [[4], [24], [39]]
    :output-type "integer"
    :test-limit 20
    :tags #{:math}}

   {:name- "filter consonants and vowels"
    :args ["string"]
    :function (fn [s]
                (let [vowels (filter #(#{\a \e \i \o \u \A \E \I \O \U} %) s)
                      consonants (filter #(#{\b \c \d \f \g \h \j \k \l \m \n \p \q \r \s \t \v \w \x \y \z
                                            \B \C \D \F \G \H \J \K \L \M \N \P \Q \R \S \T \V \W \X \Y \Z} %) s)
                      digits (filter #(#{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9} %) s)]
                  (str (apply str vowels) (apply str consonants) (apply str digits))))
    :verifications [["paradise"], ["sunshine"], ["elephant"]]
    :output-type "string"
    :test-limit 20
    :tags #{:string}}

   {:name- "interleave characters"
    :args ["string" "string"]
    :function (fn [a b]
                (let [interleaved (mapcat vector a b)
                      ; Handle remaining characters if strings are of unequal lengths
                      longer (if (> (count a) (count b)) (drop (count b) a) (drop (count a) b))]
                  (str (apply str interleaved) (apply str longer))))
    :verifications [["abc", "123"], ["clojure", "123"], ["short", "longer"]]
    :output-type "string"
    :test-limit 20
    :tags #{:string}}

   ])
