(ns sherlockbench.config)

(def benchmark-version "0.1.0")
;; Default test limit (fallback if a problem doesn't define one)
(def default-test-limit 20)

;; Atom to store loaded configuration
(def config-atom (atom {}))

;; Function to access config values
(defn get-config [& keys]
  (get-in @config-atom keys))

;; Configuration loaded from config.edn
(def config @config-atom)

;; Get all available problem sets
(defn available-problem-sets []
  (let [problem-sets (get-in @config-atom [:problem-sets])
        namespaces (:namespaces @config-atom)
        tag-names (:tag-names @config-atom)]
    
    ;; Combine built-in namespace problem sets with custom ones
    (merge
     ;; Create problem sets for each namespace (namespace/all)
     (reduce-kv
      (fn [acc ns-tag ns-data]
        (let [ns-all-key (keyword (str (name ns-tag) "/all"))]
          (assoc acc ns-all-key
                 {:name (str (:name ns-data) " (All)")
                  :description (str "All problems from " (:name ns-data))
                  :auto true})))
      {}
      namespaces)
     
     ;; Include custom problem sets from config
     problem-sets)))
