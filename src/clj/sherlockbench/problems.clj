(ns sherlockbench.problems)

; the list of functions which the LLM will interrogate

(def problems
  [
   {:name- "subtraction"
    :args ["integer" "integer"]
    :function -
    :verifications [[10, 2], [7, 5], [1, 0]]
    :output-type "integer"
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

   {:name- "ignore one"
    :args ["integer" "integer" "integer"]
    :function (fn [a b c]
                (* a c))
    :verifications [[3, 2, 9], [4, 5, 2], [0, 9, 3]]
    :output-type "integer"
    :tags #{:demo :easy3}}

   {:name- "integer division"
    :args ["integer" "integer"]
    :function quot
    :verifications [[7, 2] [15, 3] [1, 2]]
    :output-type "integer"
    :tags #{:demo}}
   ])
