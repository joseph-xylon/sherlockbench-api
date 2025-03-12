(ns sherlockbench.problem-loader
  (:require [clojure.tools.logging :as log]))

(defn load-problems
  "Safely load the problems from a namespace"
  [ns]
  (try
    (require ns)
    (let [problems-symbol (ns-resolve ns 'problems)]
      (if problems-symbol
        (do (log/info "Loaded problems from" ns)
          @problems-symbol)
        (do
          (log/warn "Namespace" ns "does not define 'problems'")
          [])))
    (catch Exception e
      (log/error "Failed to load problems from" ns ":" (.getMessage e))
      [])))

(defn aggregate-problems
  "Combine all problems from the namespaces"
  [namespaces]
  (apply concat (map load-problems (conj namespaces 'sherlockbench.problems))))