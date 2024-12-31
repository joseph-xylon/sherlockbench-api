(ns sherlockbench.validate-fn-args)

(defn coerce-integer [s]
  (try
    {:valid? true
     :coerced (Integer. s)}
    (catch Exception _
      {:valid? false
       :error (str "Could not convert '" s "' to integer")})))

(defn coerce-boolean [v]
  (cond
    (true? v)  {:valid? true :coerced true}
    (false? v) {:valid? true :coerced false}
    (or (= "true" v) (= "True" v))   {:valid? true :coerced true}
    (or (= "false" v) (= "False" v)) {:valid? true :coerced false}
    :else
    {:valid? false
     :error (str "Could not convert '" v "' to boolean")}))

(defn coerce-string [s]
  (if (string? s)
    {:valid? true :coerced s}
    {:valid? false
     :error (str "Value '" s "' is not a string")}))

(defn coerce-value [expected-type value]
  (case expected-type
    "integer" (coerce-integer value)
    "boolean" (coerce-boolean value)
    "string"  (coerce-string value)
    ;; If the pattern has an unknown type
    {:valid? false
     :error (str "Unknown type: " expected-type)}))

(defn validate-and-coerce [pattern values]
  (if (not= (count pattern) (count values))
    {:valid? false
     :coerced nil
     :errors ["Count mismatch between pattern and values"]}
    (let [results     (mapv coerce-value pattern values)
          all-valid?  (every? :valid? results)]
      (if all-valid?
        ;; If they're all valid, return just the coerced values
        {:valid?  true
         :coerced (mapv :coerced results)}
        ;; Otherwise, gather errors
        {:valid?  false
         :coerced nil
         :errors  (->> (map-indexed vector results)
                       (keep (fn [[idx {:keys [valid? error]}]]
                               (when-not valid?
                                 ;; You could enrich this with the original value or expected type
                                 (str "Index " idx ": " error)))))
         }))))

(comment
 (def pattern ["integer" "integer"])
 (def input ["123" "456"])

 (validate-and-coerce pattern input)

 (def pattern2 ["boolean" "boolean"])
 (def input2 [true "false"])

 (validate-and-coerce pattern2 input2)

 (def pattern3 ["string" "string"])
 (def input3 ["hello" "world"])

 (validate-and-coerce pattern3 input3)

 ;; errors
 (validate-and-coerce pattern3 ["boop"])
 (validate-and-coerce pattern3 [4 true])
 )
