(ns sherlockbench.core
  (:require [ring.adapter.jetty :as jetty]
            [next.jdbc :as jdbc]
            [clojure.edn :as edn]
            [integrant.core :as ig]
            [migratus.core :as migratus]
            [sherlockbench.queries :as queries]
            [sherlockbench.routes :as routes]
            [sherlockbench.add-user :refer [add-user]]
            [sherlockbench.problem-loader :as problem-loader]
            [ring.redis.session :refer [redis-store read-redis-session write-redis-session]])
  (:gen-class))

(defn read-edn-file [file-path]
  (edn/read-string
   (slurp file-path)))

(defn read-classpath-resource [filename]
  (let [resource-stream (.getResourceAsStream (clojure.lang.RT/baseLoader) filename)]
    (if resource-stream
      (slurp (java.io.InputStreamReader. resource-stream "UTF-8"))
      (throw (Exception. (str "Resource not found: " filename))))))

;; config
(defmethod ig/init-key :credentials/db [_ {:keys [file-path]}]
  (read-edn-file file-path))

(defmethod ig/init-key :sherlockbench/config [_ {:keys [file-path]}]
  (read-edn-file file-path))

;; problems loader
(defmethod ig/init-key :sherlockbench/problems [_ {:keys [config]}]
  (let [extra-namespaces (:extra-namespaces config)
        {:keys [problems namespaces tag-names] :as result} (problem-loader/aggregate-problems extra-namespaces)
        ;; Generate problem sets within the problems component
        problem-sets (problem-loader/available-problem-sets result (:problem-sets config))]
    
    ;; Return the complete, self-contained result including problem sets
    (assoc result :problem-sets problem-sets)))

;; db connection
(defn connect-db
  "reads the credentials and returns a connection object"
  [config]
  (let [my-datasource (jdbc/get-datasource (conj {:dbtype "postgresql"} config))
        connection (jdbc/get-connection my-datasource)]
    connection))

(defmethod ig/init-key :db.sql/connection [_ {:keys [db-credentials]}]
  (connect-db db-credentials))

(defmethod ig/halt-key! :db.sql/connection [_ conn]
  (.close conn))

;; handler
(defmethod ig/init-key :sherlockbench/handler [_ {:keys [queryfn session-store problems]}]
  ;; The problems component is now fully self-contained with all necessary information
  (routes/app queryfn session-store problems))

;; query builder
(defmethod ig/init-key :sherlockbench/queryfn [_ {:keys [database]}]
  (fn [& args]
    (apply queries/execute-query database args)))
 
;; session store
(defmethod ig/init-key :sherlockbench/session-store [_ {:keys [redis-config]}]
  (redis-store redis-config))

;; jetty
(defmethod ig/init-key :ring.adaptor/jetty [_ {:keys [handler opts]}]
  (jetty/run-jetty handler opts))

(defmethod ig/halt-key! :ring.adaptor/jetty [_ server]
  (.stop server))

;; migratus
(defmethod ig/init-key :migratus/config [_ {:keys [database]}]
  {:store :database
   :migration-dir "migrations/"
   :db {:connection database
        :managed-connection? true}})

;; integrant
(defn system-config
  "app or db"
  [system-type]
  (ig/read-string (read-classpath-resource (case system-type
                           :app "system.edn"
                           :db  "db-system.edn"))))

(defn halt-system [system]
  (ig/halt! system))

(defn print-usage []
  (let [infos ["Usage:"
               ""
               "Please provide a single argument:"
               "- run-app: this will run the application"
               "- migrate: this will setup or update the database tables"
               "- rollback: revert the latest db migration"]]
    (println (reduce #(str %1 "\n" %2) infos))))

(defn start-system
  "app or db"
  [system-type]
  (let [system (ig/init (system-config system-type))]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(halt-system system)))
    ;; return the system to the caller
    system))

(defn -main [& args]
  (case (first args)
    "run-app"  (let [system (start-system :app)])
    "migrate"  (let [system (start-system :db)]
                (migratus/migrate (:migratus/config system)))
    "rollback" (let [system (start-system :db)]
                 (migratus/rollback (:migratus/config system)))
    "add-user" (let [system (start-system :db)]
                 (add-user (:sherlockbench/queryfn system)))
    (print-usage)))

(comment

  )
