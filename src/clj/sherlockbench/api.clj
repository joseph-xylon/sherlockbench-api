(ns sherlockbench.api
  (:require [sherlockbench.config :refer [benchmark-version msg-limit]]
            [sherlockbench.queries :as q]
            [sherlockbench.problems :refer [problems]]))

(defn start-anonymous-run
  "start an anonymous run. this means small test set"
  [{queryfn :queryfn}]
  (let [problems' (filter #(:demo (:tags %)) problems) ; the pertinent subset of the problems
        run-id (queryfn (q/create-run! benchmark-version msg-limit))
        attempts (for [p problems'      ; 1 attempt per problem
                       :let [attempt (queryfn (q/create-attempt! run-id p))]]
                   attempt)]
   {:status 200
    :headers {"Content-Type" "application/json"}
    :body {:attempts attempts}}))
