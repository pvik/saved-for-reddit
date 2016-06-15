(ns saved-for-reddit.core
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cemerick.url :as url]
            [saved-for-reddit.reddit-api :refer [authorize]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(println "This text is printed from src/saved-for-reddit/core.cljs. Go ahead and edit it and see reloading in action.")

(println (:query (url/url (-> js/window .-location .-href))))
;; define your app data so that it doesn't get over-written on reload


(def app-state (r/atom {:username ""}))

(defn set-app-state-field [field value]
  (swap! app-state update-in [field] #(str value))
  value)

(defn make-remote-call [endpoint]
  (go (let [response (<! (http/get endpoint {:with-credentials? false}))]
        ;;enjoy your data
        (js/console.log (:body response)))))

(defn logging-in [user]
  (make-remote-call "http://clojurescriptmadeeasy.com/resources/clojure-langs.json")
  [:p "Logging in as " @user])

(defn login-html2 []
  (let [username (r/atom "")
        password (r/atom "")]
    [:form
     "User: "
     [:input {:type "text"
              :name "user"
              :id "user"
              :on-change #(reset! username (-> % .-target .-value))}]
     [:br]
     "Password: "
     [:input {:type "password"
              :name "password"
              :id "password"
              :on-change #(reset! password (-> % .-target .-value))}]
     [:br]
     [:input {:type "button" :value "Login"
              :on-click (fn []
                          (r/render-component [logging-in (set-app-state-field :username username)]
                                              (.getElementById js/document "status")))}]
     [:div {:id "status"}]]))

(defn login-html []
  (let [username (r/atom "")
        password (r/atom "")]
    [:form
     "User: "
     [:input {:type "text"
              :name "user"
              :id "user"
              :on-change #(reset! username (-> % .-target .-value))}]
     [:br]
     "Password: "
     [:input {:type "password"
              :name "password"
              :id "password"
              :on-change #(reset! password (-> % .-target .-value))}]
     [:br]
     [:input {:type "button" :value "Login"
              :on-click authorize}]
     [:div {:id "status"}]]))


(r/render-component [login-html] (.getElementById js/document "app"))

;; (defonce app-state (atom {:text "Hello world!"}))

;; (defn hello-world []
;;   [:h1 (:text @app-state)])

;; (r/render-component [hello-world]
;;                     (. js/document (getElementById "app")))



;; (defn on-js-reload []
;;   ;; optionally touch your app-state to force rerendering depending on
;;   ;; your application
;;   ;; (swap! app-state update-in [:__figwheel_counter] inc)
;; )
