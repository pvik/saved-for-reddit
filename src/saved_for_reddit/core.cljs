(ns saved-for-reddit.core
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [clojure.core.reducers :as reducers]
            [cemerick.url :as url]
            [clojure.walk :refer [keywordize-keys]]
            [alandipert.storage-atom :refer [local-storage]]
            [dommy.core :as dommy]
            [saved-for-reddit.reddit-api :refer [gen-reddit-auth-url
                                                 refresh-reddit-auth-token
                                                 get-saved-posts
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

(def client-id "ZZ370hqcmUVsRQ")
(def redirect-uri "http://127.0.0.1:3449/")

;; save app data in local-storage so that it doesn't get over-written on page reload
(def app-state (local-storage (r/atom {:username ""
                                       :token ""
                                       :after nil})
                              :saved-for-reddit-app-state))

(def saved-posts (r/atom []))
(def subreddits (r/atom {}))
(def get-posts? (r/atom true))

(defn clear-and-refresh-app []
  (alandipert.storage-atom/remove-local-storage! :saved-for-reddit-app-state)
  (set! (.-location js/window) "/"))

(defn get-username [token]
  ;; request username
  (js/console.log "Acquiring username...")
  (go (let [response (<! (http/get "https://oauth.reddit.com/api/v1/me"
                                   {:with-credentials? false
                                    :oauth-token token}))
            body  (:body response)
            error (:error body)]
        #_(println response)
        (if (or (nil? error) (clojure.string/blank? error))
          (let [username (:name body)]
            (set-app-state-field :username username)
            (r/render-component [loggedin-html username] (dommy/sel1 :#loggedin))
            (get-all-saved-posts token username saved-posts))
          (handle-error (str error " " (:error-text response) "\nYour API token might've expired."))))))

(defn reddit-request-auth-token [client-id redirect-uri code]
  ;; request reddit api token from code provided by reddit
  (go (let [response (<! (http/post "https://www.reddit.com/api/v1/access_token"
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
            access-token (:access_token body)
            ]
        #_(println response) ;; will it block here till body is available?
        (if (clojure.string/blank? error)
          (set-app-state-field :token access-token)
          (handle-error error)))))

;; Starting point
;; making sure reddit api is initialized properly and proceed accordingly
(defn init []
  (println "Initiazlizing...")
  (println @app-state)
  (let [query-vars (keywordize-keys (:query (url/url (-> js/window .-location .-href)))) ;; obtain all GET query params, if present
        code (:code query-vars)
        state (:state query-vars)
        error (:error query-vars)]
    (if (nil? error)
      ;; reddit auth request didnt give an error back
      (do
        (if (and (nil? code) (clojure.string/blank? (:token @app-state))) ;; code query param is not present or the token is blank in HTML5 localStorage
          ;; redirect to reddit for requesting authorization
          (set! (.-location js/window) (gen-reddit-auth-url client-id redirect-uri "abcdef"))
          ;; code query param is not nil or token is populated in app state
          (do
            (if  (clojure.string/blank? (:token @app-state))
              ;; app-state does not contain auth-token
              ;; retreive auth-token from reddit api
              (reddit-request-auth-token client-id redirect-uri code))
            ;; access-token should exist in app-state by this point
            (get-username (:token @app-state))
            (go (js/setTimeout #(refresh-reddit-auth-token client-id redirect-uri) 3300000)))
          )
        ;; all reddit auth and setup should be done
        (r/render-component [loggedin-html (:username @app-state)] (dommy/sel1 :#loggedin))
        (r/render-component [search-bar-html] (dommy/sel1 :#search-form))
        (r/render-component [main-html app-state saved-posts] (dommy/sel1 :#app)))
      (handle-error error))))

;; initialize the HTML page
(set! (.-onload js/window) init)

;; (defn on-js-reload []
;;   ;; optionally touch your app-state to force rerendering depending on
;;   ;; your application
;;   ;; (swap! app-state update-in [:__figwheel_counter] inc)
;; )
