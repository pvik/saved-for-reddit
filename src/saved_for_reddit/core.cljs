(ns saved-for-reddit.core
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cemerick.url :as url]
            [clojure.walk :refer [keywordize-keys]]
            [saved-for-reddit.reddit-api :refer [gen-reddit-auth-url request-reddit-auth-token]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(println "This text is printed from src/saved-for-reddit/core.cljs. Go ahead and edit it and see reloading in action.")

(def client-id "ZZ370hqcmUVsRQ")
(def redirect-uri "http://127.0.0.1:3449/")

;; define your app data so that it doesn't get over-written on reload
(def app-state (r/atom {:username ""
                        :code ""
                        :token ""}))

(def error-msg (r/atom ""))

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

(defn error-html []
  [:div [:h4 "Error"]
   [:pre @error-msg]])

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

(defn status-html []
  [:p "Received code : " (:code @app-state)])

(defn handle-reddit-authorization-response [code state]
  (set-app-state-field :code code)
  (request-reddit-auth-token code redirect-uri)
  (r/render-component [status-html] (.getElementById js/document "app")))

(defn init []
  (println "Initiazlizing...")
  (let [query-vars (keywordize-keys (:query (url/url (-> js/window .-location .-href))))
        code (:code query-vars)
        state (:state query-vars)
        error (:error query-vars)]
    (if (nil? error)
      (if (nil? code)
        (set! (.-location js/window) (gen-reddit-auth-url client-id redirect-uri "abcdef"))
        (handle-reddit-authorization-response code state)  )
      (let [] ;; handling error
        (swap! error-msg #(str error))
        (r/render-component [error-html] (.getElementById js/document "app"))))))

;; initialize the HTML page in unobtrusive way
(set! (.-onload js/window) init)

;; (defonce app-state (atom {:text "Hello world!"}))

;; (defn on-js-reload []
;;   ;; optionally touch your app-state to force rerendering depending on
;;   ;; your application
;;   ;; (swap! app-state update-in [:__figwheel_counter] inc)
;; )
