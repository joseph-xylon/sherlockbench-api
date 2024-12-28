(ns sherlockbench.problems)

; the list of functions which the LLM will interrogate

(def problems
  [
   {:name- "add & subtract"
    :args ["integer" "integer" "integer"]
    :function (fn [a b c]
                (- (+ a b) c))
    :verifications [[1, 2, 3], [10, 5, 2], [7, 2, 7]]
    :output-type "map"
    :tags #{:demo :easy3}}

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
    :verifications [[2] [7] [4] [1] [13] [25]]
    :output-type "boolean"
    :tags #{:demo :easy3}}

   {:name- "modulus 3 to fruit"
    :args ["integer"]
    :function (fn [n]
                (case (mod n 3)
                  0 "banana"
                  1 "apple"
                  2 "orange"))
    :verifications [[17], [12], [10]]
    :output-type "string"
    :tags #{:demo :easy3}}

   {:name- "ignore one"
    :args ["integer" "integer" "integer"]
    :function (fn [a b c]
                (* a c))
    :verifications [[3, 2, 9], [4, 5, 2], [0, 9, 3]]
    :output-type "integer"
    :tags #{:demo}}

   {:name- "count vowels"
    :args ["string"]
    :function (fn [s]
                (count (filter #(#{\a \e \i \o \u \A \E \I \O \U} %) s)))
    :verifications [["vector"], ["ocean"], ["strength"]]
    :output-type "integer"
    :tags #{:demo}}

   {:name- "add with toggle sign"
    :args ["integer" "integer" "boolean"]
    :function (fn [a b c]
                (let [sum (+ a b)]
                  (if c
                    (- sum)
                    sum)))
    :verifications [[4, 5, false],[7, 3, false], [10, -2, true]]
    :output-type "integer"
    :tags #{:demo}}

   {:name- "concat nth char"
    :args ["string" "string" "integer"]
    :function (fn [a b c]
                (str a (nth b c)))
    :verifications [["hello", "world", 2], ["abc", "defg", 3], ["clojure", "program", 4]]
    :output-type "string"
    :tags #{:demo}}

   {:name- "times three and round"
    :args ["integer"]
    :function (fn [n]
                (* 5 (Math/round (/ (* 3 n) 5.0))))
    :verifications [[4], [7], [8]]
    :output-type "integer"
    :tags #{:demo}}

   {:name- "filter consonants and vowels"
    :args ["string"]
    :function (fn [s]
                (let [vowels (filter #(#{\a \e \i \o \u \A \E \I \O \U} %) s)
                      consonants (filter #(not= (#{\a \e \i \o \u \A \E \I \O \U} %) %) s)]
                  (str (apply str vowels) (apply str consonants))))
    :verifications [["silent smart"], ["rapid cold"], ["quick cat"]]
    :output-type "string"
    :tags #{:demo}}

   {:name- "interleave characters"
    :args ["string" "string"]
    :function (fn [a b]
                (let [interleaved (mapcat vector a b)
                      ; Handle remaining characters if strings are of unequal lengths
                      longer (if (> (count a) (count b)) (drop (count b) a) (drop (count a) b))]
                  (str (apply str interleaved) (apply str longer))))
    :verifications [["abc", "123"], ["hello", "world"], ["clojure", "123"], ["short", "longer"], ["a", "BCD"], ["", "empty"], ["nonempty", ""]]
    :output-type "string"
    :tags #{:demo}}

   ])
