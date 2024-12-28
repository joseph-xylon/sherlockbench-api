(ns sherlockbench.validate-fn-args)

(defn coerce-integer [s]
  (try
    (Integer. s)
    (catch Exception _ nil)))

(defn coerce-boolean [s]
  (case s
    "true" true
    "True" true
    "false" false
    "False" false
    nil))

(defn coerce-string [s]
  (if (string? s)
    s
    nil))

(defn coerce-value [type value]
  (case type
    "integer" (coerce-integer value)
    "boolean" (coerce-boolean value)
    "string" (coerce-string value)
    nil))

(defn validate-and-coerce [pattern values]
  ;; (prn "pattern: " pattern)
  ;; (prn "values: " values)
  (if-not (= (count pattern) (count values))
    {:valid? false :coerced nil}
    (let [coerced (mapv coerce-value pattern values)]
      (if (every? some? coerced)
        {:valid? true :coerced coerced}
        {:valid? false :coerced nil}))))

(comment
 (def pattern ["integer" "integer"])
 (def input ["123" "456"])

 (validate-and-coerce pattern input)

 (def pattern2 ["boolean" "boolean"])
 (def input2 ["true" "false"])

 (validate-and-coerce pattern2 input2)

 (def pattern3 ["string" "string"])
 (def input3 ["hello" "world"])

 (validate-and-coerce pattern3 input3)

 (validate-and-coerce pattern3 ["boop"])
 )
