(ns sherlockbench.problem-loader
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]))

(comment
  ;; When read read in the problems we want to build a data structure like
  ;; this:
  {"Sample Problems"
   {:sherlockbench.sample-problems/all {:name ""
                                        :problems []}
    :sherlockbench.sample-problems/easy3 {:name ""
                                          :problems []}
    :sherlockbench.sample-problems/math {:name ""
                                         :problems []}}

   "Classic Problems"
   {:extra.classic/all {:name ""
                        :problems []}}

   "Custom Problems"
   {:custom-myfavs {:name ""
                    :problems []}}}
  )

(defn assemble-tagged-set
  [tag problems]
  (filterv (comp tag :tags) problems))

(defn rfn
  [problems acc tag name]
  (assoc acc tag {:name name
                  :problems (assemble-tagged-set tag problems)}))

(defn load-problems
  "Safely load the problems from a namespace"
  [ns]
  (require ns)
  (let [[problems-symbol name-symbol tags-symbol] (map #(ns-resolve ns %) ['problems 'namespace-name 'tag-names])]
    (if (and problems-symbol name-symbol tags-symbol)
      (do (log/info "Loaded problems from" ns)
          {@name-symbol (reduce-kv (partial rfn @problems-symbol) {} @tags-symbol)})
      (do
        (log/warn "Problem loading namespace " ns)
        []))))

(defn string-to-tag
  [s]
  (->> s
       (str/lower-case)
       (re-seq #"[a-z0-9]")
       (apply str "custom-")
       keyword))

(defn flatten-problems
  "get all the problems out of the data-structure, adding a key for namespace"
  [ns-problems]
  (let [all-problems (for [namespace-map (vals ns-problems)
                           [ns problem-map] namespace-map
                           problem (:problems problem-map)]
                       (assoc problem :namespace (namespace ns)))]
    all-problems))

(defn filter-by-name
  "Returns a vector of maps from coll where the namespace and name match.
   For each value in values, finds the first matching problem."
  [coll values]
  (vec
   (for [v values
         :let [match (first (filter (fn [{:keys [namespace name-]}]
                                      (= v (list (str namespace) name-)))
                                    coll))]
         :when match]
     match)))

(defn filter-by-tags
  "Returns problems that match any of the given tags, with duplicates removed"
  [maps tag-values]
  (let [tag-set (set tag-values)
        matching-problems (filterv
                          (fn [m]
                            (boolean (some tag-set (:tags m))))
                          maps)]
    ;; De-duplicate based on namespace and name combination
    (vec (vals (reduce (fn [acc problem]
                        (let [key [(:namespace problem) (:name- problem)]]
                          (assoc acc key problem)))
                      {}
                      matching-problems)))))

(defn custom-rfn
  [ns-problems acc name {:keys [tags names]}]
  (let [flat-problems (flatten-problems ns-problems)
        problems (concat (filter-by-name flat-problems names)
                         (filter-by-tags flat-problems tags))]
    (assoc acc (string-to-tag name) {:name name
                                     :problems problems})))

(defn assemble-custom-problem-sets
  [custom-problem-sets namespace-problems]
  (reduce-kv (partial custom-rfn namespace-problems) {} custom-problem-sets)
  )

(defn aggregate-problems
  "Combine all problems from the namespaces"
  [namespaces custom-problem-sets]
  (let [namespace-list (conj namespaces 'sherlockbench.sample-problems)
        namespace-problems (reduce conj {} (map load-problems namespace-list))
        custom-problems (assemble-custom-problem-sets custom-problem-sets namespace-problems)
        ]
    custom-problems
    ;;namespace-problems
    ))

(comment
  (pprint (load-problems 'sherlockbench.sample-problems))
  (aggregate-problems ['extra.classic-problems 'extra.interrobench-problems]
                      {"My Favorites" {:names [(list "sherlockbench.sample-problems" "add & subtract")
                                               (list "extra.interrobench-problems" "find-higher-number")]}})



  (pprint (flatten-problems (aggregate-problems ['extra.classic-problems] [])))
  (flatten-problems (aggregate-problems ['extra.classic-problems] []))
  )
