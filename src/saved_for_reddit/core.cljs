(ns saved-for-reddit.core
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!
                                     >!
                                     chan]]
            [clojure.core.reducers :as reducers]
            [cemerick.url :as url]
            [clojure.walk :refer [keywordize-keys]]
            [alandipert.storage-atom :refer [local-storage]]
            [dommy.core :as dommy]
            [saved-for-reddit.redditapi :refer [refresh-reddit-auth-token
                                                get-all-saved-posts
                                                set-app-state-field]]
            [saved-for-reddit.views :refer [handle-error
                                            error-html
                                            main-html
                                            post-html
                                            loggedin-html
                                            search-bar-html]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

;; helper function to print to js/console
(defn console-log [msg & more-msgs]
  (.log js/console (str msg more-msgs)))

;; setting reddit-api properties for dev or prod
(if (= "pvik.github.io" (get (clojure.string/split (str (.-location js/window)) #"/") 2))
  (do
    (defonce client-id "yeFMw-zslyBvPQ")
    (defonce redirect-uri "https://pvik.github.io/saved-for-reddit/"))
  (do
    (defonce client-id "ZZ370hqcmUVsRQ")
    (defonce redirect-uri "http://127.0.0.1:3449/")))
;; redirect-uri has to be hardcoded,
;; reddit api requires this to be exactly as specified in the app details
;; retreive the redirect uri from browser location
;;(def redirect-uri (str "http://" (get (clojure.string/split (str (.-location js/window)) #"/") 2) "/"))

;; check if :saved-for-reddit-app-state already exists in localStorage
(def app-state-exists? (atom false))
(let [localstr (.-localStorage js/window)]
  (for [c (range js/localStorage.length)]
    (if (= "saved-for-reddit-app-state"
           (re-find #"saved-for-reddit-app-state" (.key js/localStorage c)))
      (do
        (.log js/console c)
        (reset! app-state-exists? true)))))

(if (not app-state-exists?)
  (console-log "app-state-does not exists"))

;; save app data in local-storage so that it doesn't get over-written on page reload
(defonce app-state (local-storage (r/atom {:username ""
                                           :token ""
                                           :after nil
                                           :state ""})
                                  :saved-for-reddit-app-state))

(def saved-posts (r/atom {}))
(def subreddits (r/atom {}))
(def get-posts? (r/atom true))


(defn clear-and-refresh-app []
  (alandipert.storage-atom/remove-local-storage! :saved-for-reddit-app-state)
  (set! (.-location js/window) "/"))

;; generate the URL for Reddit API OAuth
(defn reddit-auth-url [client-id redirect-uri state]
  (let [cem-url (url/url "https://www.reddit.com/api/v1/authorize")
        query-param  {:client_id client-id
                      :response_type "code"
                      :state state
                      :redirect_uri redirect-uri
                      :duration "temporary"
                      :scope "history,identity,save"}]
    (str (assoc cem-url :query query-param))))

(defn reddit-get-username [token callback]
  ;; request username
  (console-log "in get-username")
  (go (let [response (<! (http/get "https://oauth.reddit.com/api/v1/me"
                                   {:with-credentials? false
                                    :oauth-token token}))
            body  (:body response)
            error (:error body)]
        (console-log body)
        (if (or (nil? error) (clojure.string/blank? error))
          (let [username (:name body)]
            (set-app-state-field :username username))
          (handle-error (str error " " (:error-text response) "\nYour API token might've expired."))))
      (if (not (nil? callback))
        (>! callback (:username @app-state)))))

(defn reddit-request-auth-token [client-id redirect-uri code state callback]
  ;; request reddit api token from code provided by reddit
  (console-log "in reddit-request-auth-token")
  (console-log "checking is state returned from reddit api is same as randomly generated in webapp")
  (console-log (str "state in app-state is " (:state @app-state)))
  (if (not (= state (:state @app-state)))
    (handle-error "Invalid state returned from reddit."))
  (go
    (let [response (<! (http/post "https://www.reddit.com/api/v1/access_token"
                                  {:with-credentials? false
                                   :basic-auth {:username client-id :password ""}
                                   :form-params {:grant_type "authorization_code"
                                                 :redirect_uri redirect-uri
                                                 :code code}}))
          ;;{:keys [status body]} response
          status (:status response)
          body   (:body response)
          ;;{:keys [error access-token]} body
          error  (:error body)
          access-token (:access_token body)]
      (console-log response) ;; will it block here till body is available?
      (if (clojure.string/blank? error)
        (set-app-state-field :token access-token)
        (handle-error error)))
    (if (not (nil? callback))
      (>! callback (:token @app-state)))))

;; Starting point
;; making sure reddit api is initialized properly and proceed accordingly
(defn init []
  (console-log "Initiazlizing...")
  (console-log @app-state)
  (let [query-vars (keywordize-keys (:query (url/url (-> js/window .-location .-href)))) ;; obtain all GET query params, if present
        code (:code query-vars)
        state (:state query-vars)
        error (:error query-vars)]
    (if (nil? error)
      ;; reddit auth request didnt give an error back
      (do
        (if (and (nil? code) (clojure.string/blank? (:token @app-state)))
          ;; code query param is not present or the token is blank in HTML5 localStorage
          ;; redirect to reddit for requesting authorization
          (let [gen-state (clojure.string/replace (str (rand 50)) "." "")]
            (set-app-state-field :state gen-state)
            (console-log @app-state)
            (set! (.-location js/window) (reddit-auth-url client-id redirect-uri gen-state)))
          ;; code query param is not nil or token is populated in app state
          (do
            (console-log @app-state)
            (if (clojure.string/blank? (:token @app-state))
              ;; app-state does not contain auth-token
              ;; retreive auth-token from reddit api
              (let [auth-token-callback-chan (chan)
                    username-callback-chan (chan)]
                (console-log "acquiring auth token")
                (reddit-request-auth-token client-id redirect-uri code state auth-token-callback-chan)
                ;; start process that waits for auth-token; before obtaining username
                (go (let [token (<! auth-token-callback-chan)]
                      (console-log "received callback from request-auth-token")
                      (console-log (str "reeceived token " token))
                      (reddit-get-username token username-callback-chan)))
                ;; starts process that waits for username; before retreiving all posts
                (go (let [username (<! username-callback-chan)]
                      (console-log "received callback from get-username")
                      (get-all-saved-posts (:token @app-state) username saved-posts))))
              ;; all reddit auth and setup is already in app-state
              ;;  retreive all saved posts
              (get-all-saved-posts (:token @app-state) (:username @app-state) saved-posts))))
        ;; render html doms
        (console-log subreddits)
        (r/render-component [loggedin-html (:username @app-state)] (dommy/sel1 :#loggedin))
        (r/render-component [search-bar-html] (dommy/sel1 :#search-form))
        (r/render-component [main-html app-state saved-posts subreddits] (dommy/sel1 :#app)))
      (handle-error error))))

;; initialize the HTML page
(set! (.-onload js/window) init)

;; (defn on-js-reload []
;;   ;; optionally touch your app-state to force rerendering depending on
;;   ;; your application
;;   ;; (swap! app-state update-in [:__figwheel_counter] inc)
;; )
