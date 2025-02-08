(ns sherlockbench.shared)

(defn log
  "concatenate and print to console"
  [& strings]
  ((.-log js/console) (reduce str strings)))

(defn byid
  "shortcut for getting element by id"
  [id]
  (.getElementById js/document id))

(defn byclass
  "shortcut for getting element by class"
  [name]
  (.getElementsByClassName js/document name))

(defn clearform
  "just reset the form"
  []
  (.reset (.getElementById js/document "pageform")))

(defn setup-form-handlers
  []
  ;; html produces this event https://htmx.org/headers/hx-trigger/
  (.addEventListener js/document.body "clearform" clearform))
