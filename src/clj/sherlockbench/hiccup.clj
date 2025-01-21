(ns sherlockbench.hiccup
    (:require [hiccup2.core :as h]))

(defn render-base
  "the basic structure of an html document. takes a title and list of elements"
  [title contents]
  (->> [:html {:lang "en"}
        [:head
         [:title title]
         [:link {:rel "stylesheet" :href "/web/public/style/style.css"}]
         [:meta {:name "viewport"
                 :content "width=device-width, initial-scale=1.0"}]
         [:meta {:charset "UTF-8"}]
         [:script {:src "https://unpkg.com/htmx.org@2.0.4"
          :integrity "sha384-HGfztofotfshcF7+8n44JQL2oJmowVChPTg48S+jvZoztPfvwD79OC/LTtG6dMp+"
          :crossorigin "anonymous"}]
         [:script {:src "https://unpkg.com/htmx-ext-json-enc@2.0.1/json-enc.js"}]]
        [:body
         [:header [:h1 title]]
         (into [:main]
               contents)]]
       h/html
       (str "<!DOCTYPE html>")))

(defn render-message
  "render a message on an html page"
  [msg]
  (let [contents [:p msg]]
    (render-base "Message" [contents])))

(defn render-login
  "prompt for login deets"
  [redirect-to f-token & [errormsg]]
  (let [post-to (str "/web/login?redirect=" redirect-to)
        errorprint [:p#errormsg]
        login-form [:form {:hx-post post-to
                           :hx-ext "json-enc"
                           :hx-headers (format "{\"X-CSRF-Token\": \"%s\"}" f-token)
                           :hx-target "#errormsg"}
                    [:fieldset
                     [:legend "Please login"]
                     [:label {:for "username"} "Username"]
                     [:input#username {:type "text" :name "username"}]
                     [:br]
                     [:label {:for "password"} "Password"]
                     [:input#password {:type "password" :name "password"}]
                     [:br]]
                    
                    [:input {:type "submit" :value "Login"}]]]
    (render-base "Login" [errorprint login-form])))

(defn render-runs
  "render a table with a list of the runs"
  [runs]
  (let [table [:table
               [:thead
                [:tr
                 [:td ""]
                 [:td "id"]
                 [:td "config"]
                 [:td "start time"]
                 [:td "score"]]]
               
               [:tbody
                (for [{:keys [id config datetime_start score_percent]} runs]
                  [:tr
                   [:td [:input {:type "checkbox" :name "run_id" :value id}]]
                   [:td id]
                   [:td config]
                   [:td datetime_start]
                   [:td score_percent]
                   ])]]]
    (render-base "Runs" [table])))
