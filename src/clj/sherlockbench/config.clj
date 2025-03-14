(ns sherlockbench.config)

(def benchmark-version "0.1.0")
;; Default test limit (fallback if a problem doesn't define one)
(def default-test-limit 20)

;; Configuration accessors - will use directly passed config map 
;; instead of an atom
(defn get-config [config & keys]
  (get-in config keys))

;; Get all available problem sets
(defn available-problem-sets [config]
  (let [problem-sets (get-in config [:problem-sets])
        namespaces (:namespaces config)
        tag-names (:tag-names config)]
    
    ;; Combine built-in namespace problem sets with custom ones and tag-based sets
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
     
     ;; Include tag-based problem sets from tag-names map
     (reduce-kv
      (fn [acc tag-key display-name]
        (assoc acc tag-key
               {:name display-name
                :description (str "Problems tagged as " (name tag-key))
                :problems {:tags #{tag-key}}
                :auto true}))
      {}
      tag-names)
     
     ;; Include custom problem sets from config
     problem-sets)))