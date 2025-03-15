(ns sherlockbench.problem-loader
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]))

;; Helper functions for working with namespaces

(defn namespace->tag
  "Convert a namespace to a tag keyword"
  [ns]
  (let [ns-str (if (symbol? ns) (str ns) ns)
        parts (str/split ns-str #"\.")
        tag-name (last parts)]
    (keyword tag-name)))

(defn create-ns-all-tag
  "Create a fully qualified keyword for 'all' problems in a namespace"
  [ns-tag]
  (keyword (str (name ns-tag) "/all")))

;; Helper functions for tag management

(defn namespace-tag-key
  "Convert a simple tag keyword to a namespaced tag using the namespace tag"
  [ns-tag tag-key]
  (if (namespace tag-key)
    tag-key  ; Already namespaced
    (keyword (name ns-tag) (name tag-key))))

(defn namespace-tag-keys
  "Convert all simple tag keywords in tag-names map to namespaced tags"
  [ns-tag tag-names]
  (reduce-kv
   (fn [acc tag-key display-name]
     (let [namespaced-key (namespace-tag-key ns-tag tag-key)]
       (assoc acc namespaced-key display-name)))
   {}
   (or tag-names {})))

(defn namespace-problem-tags
  "Processes tags in a problem definition to ensure proper namespacing and adds namespace identification tags.
   
   This function:
   1. Takes all tags in the problem's :tags set
   2. Converts simple keywords (e.g., :easy) to namespace-qualified keywords (e.g., :problems/easy)
   3. Preserves already namespace-qualified tags
   4. Adds the namespace tag itself (e.g., :problems)
   5. Adds a '/all' tag for grouping (e.g., :problems/all)
   
   Parameters:
   - problem: A problem definition map
   - ns-tag: The keyword derived from the namespace (e.g., :problems)
   
   Returns:
   The updated problem definition with properly namespaced tags."
  [problem ns-tag]
  (update problem :tags
          (fn [tags]
            (let [base-tags (or tags #{})
                  namespaced-tags (set (map #(namespace-tag-key ns-tag %) base-tags))]
              (conj namespaced-tags
                    ns-tag
                    (create-ns-all-tag ns-tag))))))

;; Main problem loading functions

(defn load-namespace-symbols
  "Safely loads a namespace and resolves problem-related vars and symbols.
   
   This function:
   1. Requires the namespace to load it into memory
   2. Resolves the 'problems var which contains problem definitions
   3. Resolves optional metadata vars: 'namespace-name and 'tag-names
   4. Catches and logs any exceptions during loading
   
   Parameters:
   - ns: A symbol representing the namespace to load (e.g., 'sherlockbench.problems)
   
   Returns a map with:
   - problems-symbol: The resolved 'problems var if found
   - namespace-name-symbol: The resolved 'namespace-name var if defined
   - tag-names-symbol: The resolved 'tag-names var if defined
   - namespace: The original namespace symbol
   - loaded?: Boolean indicating if the namespace was successfully loaded
   - error: Error message if loading failed"
  [ns]
  (try
    (require ns)
    {:problems-symbol (ns-resolve ns 'problems)
     :namespace-name-symbol (ns-resolve ns 'namespace-name)
     :tag-names-symbol (ns-resolve ns 'tag-names)
     :namespace ns
     :loaded? true}
    (catch Exception e
      (log/error "Failed to load namespace" ns ":" (.getMessage e))
      {:namespace ns
       :loaded? false
       :error (.getMessage e)})))

(defn process-loaded-namespace
  "Process a successfully loaded namespace and extract/transform its problem definitions.
   
   Takes a map from load-namespace-symbols containing:
   - problems-symbol: The resolved 'problems var from the namespace
   - namespace-name-symbol: The resolved 'namespace-name var (optional)
   - tag-names-symbol: The resolved 'tag-names var (optional)
   - namespace: The namespace symbol itself
   - loaded?: Boolean indicating if namespace was successfully loaded
   
   Returns a processed namespace data map with:
   - problems: Vector of problem maps with properly namespaced tags
   - namespace: The original namespace symbol
   - namespace-tag: Keyword derived from the namespace name
   - namespace-name: Human-readable name for the namespace
   - tag-names: Map of tag keywords to display names with properly namespaced keys
   
   If the namespace failed to load or doesn't define 'problems,
   returns a skeleton map with empty collections."
  [{:keys [problems-symbol namespace-name-symbol tag-names-symbol namespace loaded?] :as ns-data}]
  (if (and loaded? problems-symbol)
    (let [ns-tag (namespace->tag namespace)
          problems @problems-symbol
          namespace-name (when namespace-name-symbol @namespace-name-symbol)
          original-tag-names (when tag-names-symbol @tag-names-symbol)
          
          ;; Namespace the tag keys in tag-names map
          namespaced-tag-names (namespace-tag-keys ns-tag original-tag-names)
          
          ;; Add namespace tag to each problem and namespace any simple tags
          tagged-problems (map #(namespace-problem-tags % ns-tag) problems)]
      
      (log/info "Loaded" (count problems) "problems from" namespace 
                (when namespace-name (str "(" namespace-name ")")))
      
      {:problems tagged-problems
       :namespace namespace
       :namespace-tag ns-tag
       :namespace-name (or namespace-name (str namespace))
       :tag-names namespaced-tag-names})
    
    ;; Return empty data for failed loads
    (let [ns-tag (namespace->tag namespace)]
      (log/warn "Namespace" namespace "does not define 'problems'")
      {:problems []
       :namespace namespace
       :namespace-tag ns-tag
       :namespace-name (str namespace)
       :tag-names {}})))

(defn load-problems
  "Safely load the problems from a namespace and assign namespace tags.
   Each namespace should define:
   - problems: vector of problem maps
   - namespace-name: (optional) display name for the namespace
   - tag-names: (optional) map of tag keywords to display names"
  [ns]
  (-> ns
      load-namespace-symbols
      process-loaded-namespace))

;; Functions for aggregating problems from multiple namespaces

(defn extract-namespace-data
  "Extract namespace metadata from loaded namespace data"
  [acc {:keys [namespace namespace-tag namespace-name]}]
  (assoc acc namespace-tag 
         {:name namespace-name
          :namespace namespace}))

(defn extract-tag-names
  "Extract and merge tag names from loaded namespace data"
  [acc {:keys [namespace-tag namespace-name tag-names]}]
  (let [ns-all-tag (create-ns-all-tag namespace-tag)]
    (-> acc
        (assoc ns-all-tag (str namespace-name " (All)"))
        (merge tag-names))))

(defn aggregate-problems
  "Combines problem definitions from multiple namespaces and collects metadata.
   
   This function:
   1. Loads problems from sherlockbench.problems and any additional provided namespaces
   2. Combines all problem definitions into a single sequence
   3. Collects metadata about each namespace for reference
   4. Merges all tag name mappings with proper namespacing
   
   The structure allows the system to:
   - Display problems from multiple sources
   - Group problems by namespace or tags
   - Provide readable labels for UI display
   - Manage the namespacing of tags to avoid conflicts
   
   Parameters:
   - namespaces: A sequence of namespace symbols to load (e.g., ['extra.classic-problems])
   
   Returns a map with:
   - :problems - Vector of all problem definitions with properly namespaced tags
   - :namespaces - Map of namespace tags to metadata about each namespace
   - :tag-names - Combined map of fully qualified tag keywords to display names"
  [namespaces]
  (let [all-ns (conj namespaces 'sherlockbench.problems)
        loaded-data (map load-problems all-ns)
        all-problems (mapcat :problems loaded-data)
        
        ;; Collect namespace data for reference
        namespaces-data (reduce extract-namespace-data {} loaded-data)
        
        ;; Combine all tag names from all namespaces
        tag-names (reduce extract-tag-names {} loaded-data)]
    
    {:problems all-problems
     :namespaces namespaces-data
     :tag-names tag-names}))

;; Functions for creating problem sets

(defn create-namespace-problem-sets
  "Create problem sets for each namespace"
  [namespaces]
  (reduce-kv
   (fn [acc ns-tag ns-data]
     (let [ns-all-key (create-ns-all-tag ns-tag)]
       (assoc acc ns-all-key
              {:name (str (:name ns-data) " (All)")
               :description (str "All problems from " (:name ns-data))
               :auto true})))
   {}
   namespaces))

(defn create-tag-based-problem-sets
  "Create problem sets based on tags"
  [tag-names]
  (reduce-kv
   (fn [acc tag-key display-name]
     (assoc acc tag-key
            {:name display-name
             :description (str "Problems tagged as " (name tag-key))
             :problems {:tags #{tag-key}}
             :auto true}))
   {}
   tag-names))

(defn available-problem-sets 
  "Generates all available problem sets by combining namespace-based, tag-based, 
   and custom problem sets.
   
   A problem set is a named collection of problems that can be used to:
   - Group related problems for display in the UI
   - Filter problems for different types of runs or challenges
   - Organize problems by difficulty, topic, or source
   
   This function creates three types of problem sets:
   1. Namespace-based sets - All problems from a specific namespace (e.g., :problems/all)
   2. Tag-based sets - All problems with a specific tag (e.g., :problems/math)
   3. Custom sets - Manually defined in the application config
   
   Parameters:
   - problems-component: A map containing :namespaces and :tag-names from aggregate-problems
   - custom-problem-sets: A map of custom problem sets from the application config
   
   Returns:
   A map of problem set keywords to problem set definitions, where each definition has:
   - :name - Human-readable name for UI display
   - :description - Longer explanation of what's in the set
   - :problems (for tag-based sets) - Criteria for including problems
   - :auto - Flag indicating if the set was auto-generated"
  [{:keys [namespaces tag-names]} custom-problem-sets]
  (merge
   ;; Create problem sets for each namespace (namespace/all)
   (create-namespace-problem-sets namespaces)
   
   ;; Include tag-based problem sets from tag-names map
   (create-tag-based-problem-sets tag-names)
   
   ;; Include custom problem sets from config
   custom-problem-sets))