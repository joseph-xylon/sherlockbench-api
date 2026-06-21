(ns sherlockbench.random
  "Seedable randomness for procedurally-generated problems.

   A problem generates its per-attempt state in an `initfn`. By default that
   randomness comes from clojure.core/rand-int etc., so every run produces
   different instances. When a custom problem-set declares a `:seed` (see
   resources/config.edn) we instead want reproducible instances.

   To support that, an `initfn` takes a single `rng` argument and draws its
   randomness from the helpers below rather than from clojure.core directly:

       :initfn (fn [rng] {:code [(rnd/rand-int rng 9) (rnd/rand-int rng 9)]})

   The `rng` is a java.util.Random built from the problem-set's seed (shared
   across all attempts in the run, so the whole set is reproducible). When no
   seed is configured the `rng` is nil and the helpers fall back to the normal,
   non-deterministic clojure.core functions."
  (:refer-clojure :exclude [rand rand-int rand-nth]))

(defn make-rng
  "Return a seeded java.util.Random for `seed`, or nil when seed is nil."
  [seed]
  (when seed (java.util.Random. (long seed))))

(defn rand
  "Like clojure.core/rand, drawing from `rng` when one is supplied."
  ([rng] (if rng (.nextDouble ^java.util.Random rng) (clojure.core/rand)))
  ([rng n] (* n (rand rng))))

(defn rand-int
  "Like clojure.core/rand-int, drawing from `rng` when one is supplied."
  [rng n]
  (if rng
    (.nextInt ^java.util.Random rng (int n))
    (clojure.core/rand-int n)))

(defn rand-nth
  "Like clojure.core/rand-nth, drawing from `rng` when one is supplied."
  [rng coll]
  (nth coll (rand-int rng (count coll))))

(def ^:private token-alphabet
  "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")

(defn rand-string
  "Return a random alphanumeric string of `length` characters, drawn from `rng`
   when supplied. Useful for generating unique, unguessable secrets that are
   reproducible under a seed."
  [rng length]
  (apply str (repeatedly length #(rand-nth rng token-alphabet))))
