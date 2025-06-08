(ns sherlockbench.random-investigation
    (:require [clojure.string :as str]))

;; A small dictionary of words to pull from for "string" types.
(def dictionary-words
  ["apple" "banana" "cherry" "date" "elderberry" "fig" "grape"
   "honeydew" "kiwi" "lemon" "mango" "nectarine" "orange" "papaya"
   "quince" "raspberry" "strawberry" "tangerine" "ugli" "vanilla"
   "watermelon" "xigua" "yam" "zucchini"])

(defn- generate-random-value
  "Generates a single random value based on a type string."
  [type-str]
  (case type-str
    "integer" (rand-int 100)
    "string" (rand-nth dictionary-words)
    "boolean" (rand-nth [true false])
    (throw (IllegalArgumentException. (str "Unknown type spec: " type-str)))))

(defn quote-if-string [x]
  (cond
    (string? x) (str "\"" x "\"")
    (= true x) "True"
    (= false x) "False"
    :else x))

(defn rfn [f acc current-args]
  (let [result (apply f current-args)
        new-line (str "(" (str/join ", " (map quote-if-string current-args)) ") → " (quote-if-string result))]

    (if (empty? acc)
      new-line
      (str acc "\n" new-line))))

(defn random-fn-inputs
  "Generates n sets of random inputs for a function based on a type spec,
  calls the function with each set, and returns a formatted multi-line string."
  [spec f n]
  (let [all-arg-sets (repeatedly n #(mapv generate-random-value spec))
        rfn' (partial rfn f)]
    (reduce rfn' "" all-arg-sets)))

(comment
  ;; examples

  (println (random-fn-inputs ["integer", "integer"] + 3))
  (println (random-fn-inputs ["string"] true? 3))

  (let [spec ["string", "integer", "boolean"]
        f (fn [s i b] (str "The " s " is " (if b "good" "bad") " (" i "%)"))]
    (println (random-fn-inputs spec f 4))))
