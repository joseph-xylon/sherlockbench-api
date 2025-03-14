(ns sherlockbench.handlers
  (:require [clojure.set :as set]
            [sherlockbench.queries :as q]
            [sherlockbench.hiccup :as ph]
            [sherlockbench.api :as api]
            [sherlockbench.config :as config]
            [hiccup2.core :as h]))

(defn not-found-handler
  "display not found page"
  [& x]
  {:status 404
   :headers {"Content-Type" "text/html"}
   :body (ph/render-message "Page Not Found")})

(defn home-handler
  "home"
  [{queryfn :queryfn}]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (ph/render-base "Home" [[:p "SherlockBench Server is hosted here."]
                                 [:p "For the admin console, see " [:a {:href "/web/secure/runs/display"} "here"] "."]])})

(defn login-handler
  "show the login prompt. the parameter variable holds the url the user was trying
  to access before being sent here"
  [{{redirect "redirect" :or {redirect "/"}} :query-params
    f-token :anti-forgery-token}]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (ph/render-login redirect f-token)})

(defn login-post-handler
  "check the credentials. if success, redirect to the redirect url. else, display
  the login page again"
  [{queryfn :queryfn
    {redirect "redirect" :or {redirect "/"}} :query-params
    session :session
    {:keys [username password]} :body
    f-token :anti-forgery-token}]

  (if (queryfn (q/authenticate-user username password))
    ;; login success
    {:status 200
     :headers {"HX-Redirect" redirect}
     :body ""
     :session (assoc session :user username)}

    ;; login failure
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "<em>Login failed</em>"}))

(defn logout-handler
  "log out and redirect to /"
  [{session :session}]

  {:status 303
   :headers {"Location" "/"}
   :body ""
   :session (dissoc session :user)})

(defn strip-namespace [m]
  (let [rename-key (fn [k] (keyword (name k)))]
    (set/rename-keys m (zipmap (keys m) (map rename-key (keys m))))))

(defn display-runs-page
  "home"
  [{queryfn :queryfn
    f-token :anti-forgery-token}]
  (let [runs (queryfn (q/list-runs))
        problem-sets (config/available-problem-sets)]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (ph/runs-page (map strip-namespace runs) f-token problem-sets)}))

(defn delete-run-handler
  "delete a run given an id"
  [{queryfn :queryfn
    {:keys [run_id]} :body}]
  
  (doseq [id run_id]
    (queryfn (q/delete-run! id)))

  (let [runs (queryfn (q/list-runs))
        table (ph/render-runs (map strip-namespace runs))
        rendered (str (h/html table))]
    
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body rendered}))

(defn create-run-handler
  "create a run"
  [{queryfn :queryfn
    problems :problems
    {:keys [exam-set]} :body}]
  ;; Validate that the exam-set is one of the available problem sets
  (let [problem-sets (config/available-problem-sets)]
    (if-not (contains? (set (map name (keys problem-sets))) exam-set)
      {:status 400
       :headers {"Content-Type" "text/html"}
       :body (str (h/html [:p.error (str "Invalid problem set: " exam-set)]))}
      (let [problem-set (keyword exam-set)
            [run-id attempts] (api/create-run queryfn problems nil "official" "pending" problem-set)
            
            ;; now render the page
            runs (queryfn (q/list-runs))
            table (ph/render-runs (map strip-namespace runs))
            rendered (str (h/html table)
                          (h/html [:p.message (str "Created run with id: " run-id)]))]
        {:status 200
         :headers {"Content-Type" "text/html"
                   "HX-Trigger" "clearform"}
         :body rendered}))))
