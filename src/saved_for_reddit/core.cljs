(ns saved-for-reddit.core
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [clojure.core.reducers :as reducers]
            [cemerick.url :as url]
            [clojure.walk :refer [keywordize-keys]]
            [saved-for-reddit.reddit-api :refer [gen-reddit-auth-url]]
            [alandipert.storage-atom :refer [local-storage]]
            [dommy.core :as dommy]
            [cljs-time.coerce :as timec]
            [cljs-time.format :as timef]
            [goog.string :refer [unescapeEntities]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(def client-id "ZZ370hqcmUVsRQ")
(def redirect-uri "http://127.0.0.1:3449/")
(def reddit-api-uri "https://oauth.reddit.com/api/v1/")

(def time-formatter (timef/formatter "yyyy-MM-dd HH:mm"))

;; define your app data so that it doesn't get over-written on reload
(def app-state (local-storage (r/atom {:username ""
                                       :token ""})
                              :saved-for-reddit-app-state))

(def saved-posts (r/atom []))
(def error-msg (r/atom ""))

(defn set-app-state-field [field value]
  (swap! app-state update-in [field] #(str value))
  value)

(defn error-html []
  [:div {:class "row"}
   [:div {:class "col-md-12"}
    [:h4 "Error"]
    [:div {:class "alert alert-danger" :role "alert"}
     [:pre @error-msg]]
    [:input {:type "button" :value "Clear app LocalStorage and refresh!"
             :on-click (fn [] (alandipert.storage-atom/remove-local-storage! :saved-for-reddit-app-state)
                         (set! (.-location js/window) "/"))}]]])

(defn post-html [p]
  (let [link? (nil? (:title p))
        title (unescapeEntities (if link? (:link_title p) (:title p)))
        url (if link? (:link_url p) (:url p))
        body (if (not (nil? (:body_html p))) (unescapeEntities (:body_html p)))
        subreddit (:subreddit p)
        author (:author p)
        created-on-epoch-local (+ (:created_utc p) (* 3600 (.getTimezoneOffset (js/Date.))))
        created-on-str (timef/unparse time-formatter (timec/from-long (* 1000 created-on-epoch-local)))]
    [:div {:class "panel panel-default"}
     [:div {:class "panel-heading"}
      [:h4 {:class "panel-title"}
       [:a {:href url} title] " "
       [:a {:href (str "https://www.reddit.com/r/" subreddit)} [:span {:class "badge"} subreddit]]]]
     [:div {:class "panel-body"}
      [:div {:dangerouslySetInnerHTML {:__html body}}]
      [:small "submitted by " [:a {:href (str "https://www.reddit.com/user/" author)} [:small author]]
       " on " created-on-str]]]))

(defn main-html [posts]
  [:div {:class "col-md-12"}
   [:h4 "Saved Posts"]
   [:div {:class "list-group"}
    (for [p @posts]
      [post-html p])]
   [:div {:class "row"}
    [:div {:class "col-md-12"}
     [:input {:type "button" :value "moar!"
              :on-click (fn [] (println "get more posts"))}]]]
   [:p "Reddit API Token: "
    [:input {:type "text" :id "token" :name "token"
             :value (:token @app-state) :readOnly "true"}]]
   [:p "Logged in as " (:username @app-state)]])

(defn handle-error [error]
  (swap! error-msg #(str error))
  (r/render-component [error-html] (dommy/sel1 :#error)))

(defn process-json-post [post]
  (let [p (:data post)
        id (:id p)
        title (:title p)
        subreddit (:subreddit p)
        permalink (:permalink p)
        url (:url p)
        over_i8 (:over_i8 p)
        author (:author p)]
    (println id ": " title " by " author " (" url ")")))

(defn get-saved-posts []
  (js/console.log "Retreiving saved posts...")
  (go (let [response (<! (http/get (str "https://oauth.reddit.com/user/" (:username @app-state) "/saved")
                                   {:with-credentials? false
                                    :oauth-token (:token @app-state)}))
            posts (-> response :body :data :children)
            error (-> response :body :error)]
        (println "received response")
        (println response)
        (if (clojure.string/blank? error)
          (doseq [p posts]
            #_(println (process-json-post p))
            (swap! saved-posts #(conj % %2) (assoc (:data p) :key (-> p :data :id))))
          (handle-error (str error " " (:error-text response) "\nYour API token might've expired"))))))

(defn process-after-token-acquire []
  (js/console.log "Token acquired...")
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
            (get-saved-posts))
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
            (process-after-token-acquire))
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
          (r/render-component [main-html saved-posts] (dommy/sel1 :#app))
          (if (not (clojure.string/blank? (:token @app-state)))
            (do
              (process-after-token-acquire))
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
