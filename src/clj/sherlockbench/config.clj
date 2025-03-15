(ns sherlockbench.config)

(def benchmark-version "0.1.0")
;; Default test limit (fallback if a problem doesn't define one)
(def default-test-limit 20)

;; Configuration accessors - will use directly passed config map 
;; instead of an atom
(defn get-config [config & keys]
  (get-in config keys))