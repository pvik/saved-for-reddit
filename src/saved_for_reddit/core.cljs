(ns saved-for-reddit.core
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [clojure.core.reducers :as reducers]
            [cemerick.url :as url]
            [clojure.walk :refer [keywordize-keys]]
            [alandipert.storage-atom :refer [local-storage]]
            [dommy.core :as dommy]
            [saved-for-reddit.reddit-api :refer [gen-reddit-auth-url get-saved-posts set-app-state-field]]
            [saved-for-reddit.views :refer [handle-error error-html post-html loggedin-html]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(def client-id "ZZ370hqcmUVsRQ")
(def redirect-uri "http://127.0.0.1:3449/")
(def reddit-api-uri "https://oauth.reddit.com/api/v1/")

;; save app data in local-storage so that it doesn't get over-written on page reload
(def app-state (local-storage (r/atom {:username ""
                                       :token ""
                                       :after ""})
                              :saved-for-reddit-app-state))

(def saved-posts (r/atom []))


(defn main-html [token username posts]
  [:div {:class "col-md-10"}
   [:h4 "Saved Posts " [:span {:class "badge"} (count @posts)] ]
   [:div {:class "list-group"}
    (for [p @posts]
      [post-html p])]
   [:div {:class "row"}
    [:div {:class "col-md-12"}
     [:input {:type "button" :value "moar!"
              :id "btn-get-posts" :name "btn-get-posts"
              :on-click (fn [] (println "get more posts") (get-saved-posts token username saved-posts (:after @app-state)) )}]]]
   [:p "Reddit API Token: "
    [:input {:type "text" :id "token" :name "token"
             :value token :readOnly "true"}]]])

(defn get-username []
  ;; request username
  (js/console.log "Acquiring username...")
  (go (let [response (<! (http/get (str reddit-api-uri "me")
                                   {:with-credentials? false
                                    :oauth-token (:token @app-state)}))
            body  (:body response)
            error (:error body)]
        (println response)
        (if (or (nil? error) (clojure.string/blank? error))
          (do
            (set-app-state-field :username (:name body))
            (get-saved-posts (:token @app-state) (:username @app-state) saved-posts))
          (handle-error (str error " " (:error-text response) "\nYour API token might've expired."))))))

(defn request-reddit-auth-token [client-id redirect-uri code]
  (go (let [response (<! (http/post "https://www.reddit.com/api/v1/access_token"
                                    {:with-credentials? false
                                     :basic-auth {:username client-id :password ""}
                                     :form-params {:grant_type "authorization_code"
                                                   :redirect_uri redirect-uri
                                                   :code code}}))
            status (:status response)
            body   (:body response)
            error  (:error body)
            access-token (str (:access_token body))]
        (println response) ;; will it block here till body is available?
        (if  (clojure.string/blank? error)
          (do
            (set-app-state-field :token access-token)
            (get-username))
          (handle-error error)))))

(defn init []
  (println "Initiazlizing...")
  (println @app-state)
  (let [query-vars (keywordize-keys (:query (url/url (-> js/window .-location .-href))))
        code (:code query-vars)
        state (:state query-vars)
        error (:error query-vars)]
    (if (nil? error)
      (if (and (nil? code) (clojure.string/blank? (:token @app-state))) ;; code query param is not present or the token is blank in HTML5 localStorage
        (set! (.-location js/window) (gen-reddit-auth-url client-id redirect-uri "abcdef")) ;; redirect to reddit for requesting authorization
        (do
          (r/render-component [loggedin-html (:username @app-state)] (dommy/sel1 :#loggedin))
          (r/render-component [main-html (:token @app-state) (:username @app-state) saved-posts] (dommy/sel1 :#app))
          (if (not (clojure.string/blank? (:token @app-state)))
            (do
              (get-username))
            (request-reddit-auth-token client-id redirect-uri code))))
      (handle-error error))))

;; initialize the HTML page
(set! (.-onload js/window) init)

;; (defn login-html2 []
;;   (let [username (r/atom "")
;;         password (r/atom "")]
;;     [:form "User: "
;;      [:input {:type "text" :name "user" :id "user" :on-change #(reset! username (-> % .-target .-value))}]
;;      [:br]"Password: "
;;      [:input {:type "password" :name "password" :id "password" :on-change #(reset! password (-> % .-target .-value))}]
;;      [:br]
;;      [:input {:type "button" :value "Login" :on-click (fn [] (r/render-component [logging-in (set-app-state-field :username username)]
;;                                                                                 (.getElementById js/document "status")))}]
;;      [:div {:id "status"}]]))

;; (defonce app-state (atom {:text "Hello world!"}))

;; (defn on-js-reload []
;;   ;; optionally touch your app-state to force rerendering depending on
;;   ;; your application
;;   ;; (swap! app-state update-in [:__figwheel_counter] inc)
;; )
