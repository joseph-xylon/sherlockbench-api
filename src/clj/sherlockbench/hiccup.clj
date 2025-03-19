(ns sherlockbench.hiccup
    (:require [hiccup2.core :as h]
              [clojure.pprint :refer [pprint]]
              [sherlockbench.utility :as util]))

(defn render-base
  "the basic structure of an html document. takes a title and list of elements"
  [title contents & {:keys [form scripts]}]
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
         [:main
          (into [:div#replaceme]
                contents)
          form]
         (when-not (empty? scripts)
           (for [script scripts]
             [:script {:type "text/javascript" :src script}]))]]
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

(defn map-to-html [m]
  (if (empty? m)
      nil
      (reduce (fn [acc [key val]] (concat acc [(str key ": " val) [:br]])) [] m)))

(defn map-to-ratio [{numerator "numerator" denominator "denominator"}]
  (str numerator "/" denominator))

(defn render-runs
  "render a table with a list of the runs"
  [runs]
  (let [table [:table {:style "table-layout: auto; width: 100%;"}
               [:colgroup
                [:col]
                [:col]
                [:col {:style "white-space: nowrap; min-width: 12em;"}]
                [:col]
                [:col]
                [:col]
                [:col]]
               [:thead
                [:tr
                 [:td ""]
                 [:td "id"]
                 [:td "config"]
                 [:td "type"]
                 [:td "state"]
                 [:td "start time"]
                 [:td "score"]]]
               
               [:tbody
                (for [{:keys [id config datetime_start final_score run_type run_state]} runs]
                  [:tr
                   [:td [:input {:type "checkbox" :name "run_id" :value id}]]
                   [:td id]
                   [:td (map-to-html config)]
                   [:td run_type]
                   [:td run_state]
                   [:td datetime_start]
                   [:td (map-to-ratio final_score)]
                   ])]]]
    table))

(defn runs-page
  "render the runs page"
  [runs f-token problems]
  (let [table (render-runs runs)
        form [:form#pageform {:hx-ext "json-enc"
                              :hx-headers (format "{\"X-CSRF-Token\": \"%s\"}" f-token)
                              :hx-target "#replaceme"
                              :autocomplete "off"}
              [:button {:type "button"
                        :hx-post "/web/secure/runs/delete-run"
                        :hx-include "[name='run_id']"} "Delete"]
              " "
              [:select {:name "exam-set"
                        :hx-post "/web/secure/runs/create-run"}
               [:option {:value "default"} "Create"]
               (for [[group-name v] problems]
                 [:optgroup {:label group-name}
                  (for [[pset-kw {name- :name}] v
                        :let [set-id (util/problem-set-key-to-string pset-kw)]]
                    [:option {:value set-id} name-])])]]]

    (render-base "Runs" [table] :form form :scripts ["/web/public/cljs/shared.js" "/web/public/cljs/runs-list.js"])))
