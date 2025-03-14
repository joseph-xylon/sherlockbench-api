(ns sherlockbench.problem-loader
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]))

(defn namespace->tag
  "Convert a namespace to a tag keyword"
  [ns]
  (let [ns-str (if (symbol? ns) (str ns) ns)
        parts (str/split ns-str #"\.")
        tag-name (last parts)]
    (keyword tag-name)))

(defn load-problems
  "Safely load the problems from a namespace and assign namespace tags.
   Each namespace should define:
   - problems: vector of problem maps
   - namespace-name: (optional) display name for the namespace
   - tag-names: (optional) map of tag keywords to display names"
  [ns]
  (try
    (require ns)
    (let [problems-symbol (ns-resolve ns 'problems)
          namespace-name-symbol (ns-resolve ns 'namespace-name)
          tag-names-symbol (ns-resolve ns 'tag-names)
          ns-tag (namespace->tag ns)]
      
      (if problems-symbol
        (let [problems @problems-symbol
              namespace-name (when namespace-name-symbol @namespace-name-symbol)
              original-tag-names (when tag-names-symbol @tag-names-symbol)
              
              ;; Namespace the tag keys in tag-names map
              namespaced-tag-names (reduce-kv
                                    (fn [acc tag-key display-name]
                                      (let [namespaced-key (if (namespace tag-key)
                                                            tag-key
                                                            (keyword (name ns-tag) (name tag-key)))]
                                        (assoc acc namespaced-key display-name)))
                                    {}
                                    (or original-tag-names {}))
              
              ;; Add namespace tag to each problem and namespace any simple tags
              tagged-problems (map (fn [problem]
                                     (update problem :tags 
                                             (fn [tags]
                                               (let [base-tags (or tags #{})
                                                     ;; Namespace any simple keywords in the tags set
                                                     namespaced-tags (set (map (fn [tag]
                                                                                (if (namespace tag)
                                                                                  tag
                                                                                  (keyword (name ns-tag) (name tag))))
                                                                              base-tags))]
                                                 (conj namespaced-tags
                                                       ns-tag
                                                       (keyword (str (name ns-tag) "/all")))))))
                                   problems)]
          
          (log/info "Loaded" (count problems) "problems from" ns 
                    (when namespace-name (str "(" namespace-name ")")))
          
          {:problems tagged-problems
           :namespace ns
           :namespace-tag ns-tag
           :namespace-name (or namespace-name (str ns))
           :tag-names namespaced-tag-names})
        
        (do
          (log/warn "Namespace" ns "does not define 'problems'")
          {:problems []
           :namespace ns
           :namespace-tag ns-tag
           :namespace-name (str ns)
           :tag-names {}})))
    
    (catch Exception e
      (log/error "Failed to load problems from" ns ":" (.getMessage e))
      {:problems []
       :namespace ns
       :namespace-tag (namespace->tag ns)
       :namespace-name (str ns)
       :tag-names {}})))

(defn aggregate-problems
  "Combine all problems from the namespaces and collect metadata.
   Returns a map with:
   - :problems - all problems from all namespaces
   - :namespaces - metadata about each namespace
   - :tag-names - combined map of tag names from all namespaces"
  [namespaces]
  (let [all-ns (conj namespaces 'sherlockbench.problems)
        loaded-data (map load-problems all-ns)
        all-problems (mapcat :problems loaded-data)
        
        ;; Collect namespace data for reference
        namespaces-data (reduce (fn [acc {:keys [namespace namespace-tag namespace-name]}]
                                  (assoc acc namespace-tag 
                                         {:name namespace-name
                                          :namespace namespace}))
                                {}
                                loaded-data)
        
        ;; Combine all tag names from all namespaces
        tag-names (reduce (fn [acc {:keys [namespace-tag namespace-name tag-names]}]
                            (let [ns-all-tag (keyword (str (name namespace-tag) "/all"))]
                              (-> acc
                                  (assoc ns-all-tag (str namespace-name " (All)"))
                                  (merge tag-names))))
                          {}
                          loaded-data)]
    
    {:problems all-problems
     :namespaces namespaces-data
     :tag-names tag-names}))