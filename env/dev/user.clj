(ns user
  (:require [integrant.core :as ig]
            [integrant.repl :refer [clear go halt prep init reset reset-all]]
            [sherlockbench.core :as pc]
            [clojure.repl :refer [doc]]
            [migratus.core :as migratus]
            [sherlockbench.queries :as q]))

(integrant.repl/set-prep! #(ig/expand (pc/system-config :app) (ig/deprofile [:dev])))

(defn migrate []
  (migratus/migrate (:migratus/config integrant.repl.state/system)))

(defn rollback []
  (migratus/rollback (:migratus/config integrant.repl.state/system)))

(defn create-migration [name]
  (migratus/create (:migratus/config integrant.repl.state/system) name))

;; use like this: (query (q/myquery "foo" "bar"))
(defn query [& args]
  (apply (:sherlockbench/queryfn integrant.repl.state/system) args))
