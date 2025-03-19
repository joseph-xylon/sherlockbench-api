(ns sherlockbench.utility)

(defn problem-set-key-to-string
  [set-kw]
  (subs (str set-kw) 1))

(defn not-empty-string? [s]
  (and (string? s) (seq s)))
