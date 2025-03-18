(ns sherlockbench.utility)

(defn problem-set-key-to-string
  [set-kw]
  (subs (str set-kw) 1))
