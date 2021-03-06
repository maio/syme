(ns syme.html
  (:require [hiccup.page :refer [html5 doctype include-css]]
            [environ.core :refer [env]]
            [tentacles.repos :as repos]
            [tentacles.users :as users]
            [syme.db :as db]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as sql]))

(def login-url (str "https://github.com/login/oauth/authorize?"
                    "client_id=" (env :oauth-client-id)))

(defn layout [body username & [project]]
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:title (if project (str project " - Syme") "Syme")]
    (include-css "/stylesheets/style.css" "/stylesheets/base.css"
                 "/stylesheets/skeleton.css")
    (include-css "https://fonts.googleapis.com/css?family=Passion+One:700")]
   [:body
    (if-let [account (:analytics-account env)]
      [:script {:type "text/javascript"} (-> (io/resource "analytics.js")
                                             (slurp) (format account))])
    [:div#header
     [:h1.container [:a {:href "/"} "Syme"]]]
    [:div#content.container body
     [:div#footer
      [:p [:a {:href "/faq"} "About"]
       " | " [:a {:href "https://github.com/technomancy/syme"}
              "Source"]
       " | " (if username
               [:a {:href "/logout"} "Log out"]
               [:a {:href login-url} "Log in"])]]]]))

(defn splash [username]
  (layout
   [:div
    [:img {:src "/splash.png"
           :style "position: absolute; z-index: -1; top: -10px; left: -30px;"}]
    [:form {:action "/launch" :method :get :id "splash"
            :style "position: absolute; top: 257px; left: -20px; width: 440px;"}
     [:input {:type :submit :value "Collaborate on a GitHub project"
              :style "width: 48%; float: right;"}]
     [:input {:type :text :name "project"
              :style "width: 48%; height: 14px; font-weight: bold;"
              :placeholder "user/project"}]]
    [:p {:style "margin-bottom: 700px;"} "&nbsp;"]] username))

(defn faq [username]
  (layout (slurp (io/resource "faq.html")) username))

(defn launch [username repo-name identity credential]
  (let [repo (apply repos/specific-repo (.split repo-name "/"))]
    (when-not (:name repo)
      (throw (ex-info "Repository not found" {:status 404})))
    (layout
     [:div
      [:h3.project [:a {:href (:html_url repo)} repo-name]]
      [:p {:id "desc"} (:description repo)]
      [:hr]
      [:form {:action "/launch" :method :post}
       [:input {:type :hidden :name "project" :value repo-name}]
       [:input {:type :text :name "invite" :id "invite"
                :placeholder "users to invite (space-separated)"}]
       [:input {:type :text :name "identity" :id "identity"
                :value identity :placeholder "AWS Access Key"}]
       [:input {:type :text :style "width: 320px"
                :name "credential" :id "credential"
                :value credential :placeholder "AWS Secret Key"}]
       [:hr]
       [:p {:style "float: right; margin-top: 10px; font-size: 80%"}
        "Your credentials are stored in an encrypted cookie, never"
        " on the server."]
       [:input {:type :submit :value "Launch!"}]]]
     username repo-name)))

(defonce icon (memoize (comp :avatar_url users/user)))

(defn instance [username {:keys [project status description ip invitees]}]
  (layout
   [:div
    [:p {:id "status" :class status} status]
    [:h3.project [:a {:href (format "https://github.com/%s" project)} project]]
    [:p {:id "desc"} description]
    [:hr]
    (if ip
      [:div
       ;; TODO: remove inline styles
       [:p {:id "haltbutton" :style "float: right; margin: -7px 0;"}
        [:button {:onclick "show_halt()"} "Halt"]]
       [:div {:id "halt" :style "float: right; clear: right; display: none"}
        [:button {:onclick "hide_halt();"} "Cancel"]
        [:button {:onclick (format "halt('%s')" project)} "Confirm"]]
       [:p {:id "ip" :class status
            :title "Send this command to the users you've invited."}
        [:tt "ssh syme@" ip]]]
      [:p "Waiting to boot... could take a few minutes."])
    [:hr]
    [:ul {:id "users"}
     (for [u invitees]
       [:li [:a {:href (str "https://github.com/" u)}
             [:img {:src (icon u) :alt u :title u :height 80 :width 80}]]])]
    [:script {:type "text/javascript", :src "/syme.js"
              :onload (if ip
                        (format "watch_status('%s')" project)
                        (format "wait_for_boot('%s')" project))}]]
   username project))
