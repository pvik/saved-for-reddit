(ns saved-for-reddit.reddit-api
  (:require [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cemerick.url :as url]
            [cljs-time.coerce :as timec]
            [cljs-time.format :as timef]
            [dommy.core :as dommy]
            [goog.string :refer [unescapeEntities]]
            [saved-for-reddit.views :as views])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; a global time-formatter to handle how time strings are displayed
(def time-formatter (timef/formatter "yyyy-MM-dd HH:mm"))

(defn set-app-state-field [field value]
  (swap! saved-for-reddit.core/app-state update-in [field] #(str value))
  value)

#_(defn make-remote-get-call [endpoint]
    (go (let [response (<! (http/get endpoint {:with-credentials? false}))]
          ;;enjoy your data
          (js/console.log (:body response)))))

#_(defn make-remote-post-call [endpoint]
    (go (let [response (<! (http/post endpoint {:with-credentials? false}))]
          ;;enjoy your data
          (js/console.log (:body response)))))

(defn refresh-reddit-auth-token [client-id redirect-uri]
  ;; request reddit api token from code provided by reddit
  (go (let [response (<! (http/post "https://www.reddit.com/api/v1/access_token"
                                    {:with-credentials? false
                                     :basic-auth {:username client-id :password ""}
                                     :form-params {:grant_type "refresh_token"
                                                   :redirect_uri redirect-uri
                                                   :refresh_token (:token @saved-for-reddit.core/app-state)}}))
            status (:status response)
            body   (:body response)
            error  (:error body)
            access-token (:access_token body)]
        (if (clojure.string/blank? error)
          (do
            (set-app-state-field :token access-token)
            (js/setTimeout #(refresh-reddit-auth-token client-id redirect-uri) 3300000))
          (views/handle-error error)))))

(defn reddit-unsave [thing-id]
  (let [response (<! (http/post "https://www.reddit.com/api/unsave"
                                {:with-credentials? false
                                 :oauth-token "token"
                                 :id thing-id}))
        status (:status response)
        body   (:body response)
        error  (:error body)]
    (if (clojure.string/blank? error)
      (do
        (println body))
      (views/handle-error error))))

(defn repack-post [p]
  (let [id (:id p)
        link? (nil? (:title p))
        key (str (:name p) (:subreddit_id p) (:link_id p))
        title (unescapeEntities (if link? (:link_title p) (:title p)))
        url (if link? (str (:link_url p) key) (:url p))
        body (if (not (nil? (:body_html p))) (unescapeEntities (:body_html p)))
        subreddit (:subreddit p)
        author (:author p)
        permalink (if link? (:link_url p) (str "https://www.reddit.com" (:permalink p)))
        created-on-epoch-local (+ (:created_utc p) (* 3600 (.getTimezoneOffset (js/Date.))))
        created-on-str (timef/unparse time-formatter (timec/from-long (* 1000 created-on-epoch-local)))]
    {:link link? :key key :title title :url url :body body :subreddit subreddit :author author :permalink permalink :created-on created-on-str}))

(defn get-saved-posts [token username saved-posts & after]
  (let [saved-post-get-chan (if (nil? after)
                              (http/get (str "https://oauth.reddit.com/user/" username "/saved")
                                        {:with-credentials? false
                                         :oauth-token token})
                              (http/get (str "https://oauth.reddit.com/user/" username "/saved")
                                        {:with-credentials? false
                                         :oauth-token token
                                         :query-params {"after" (first after)}}))]
    (js/console.log "Retreiving saved posts..." after)
    (go (let [response (<! saved-post-get-chan )
              posts (-> response :body :data :children)
              error (-> response :body :error)
              after-str (-> response :body :data :after)
              after-ret (if (clojure.string/blank? after-str) nil after-str)]
          (println "received response")
          (if (clojure.string/blank? error)
            (do
              (set-app-state-field :after after-ret)
              (if (nil? after-ret)
                (set! (.-disabled (dommy/sel1 :#btn-get-posts)) true))
              (doseq [p posts]
                (swap! saved-posts #(conj % %2) (repack-post (:data p))))
              after-ret)
            (views/handle-error (str error " " (:error-text response) "\nYour API token might've expired")))))))

(defn update-view-after-retreive-complete []
  (set! ( .-visibility (.-style (dommy/sel1 :#btn-stop-get-posts))) "hidden")
  (set! ( .-visibility (.-style (dommy/sel1 :#loading-gif))) "hidden")
  (set! (.-disabled (dommy/sel1 :#btn-search-posts)) false)
  (set! (.-disabled (dommy/sel1 :#txt-search-posts)) false)
  (set! (.-placeholder (dommy/sel1 :#txt-search-posts)) "Search..."))

(defn get-all-saved-posts [token username saved-posts & after]
  (let [saved-post-get-chan (if (nil? after)
                              (http/get (str "https://oauth.reddit.com/user/" username "/saved")
                                        {:with-credentials? false
                                         :oauth-token token})
                              (http/get (str "https://oauth.reddit.com/user/" username "/saved")
                                        {:with-credentials? false
                                         :oauth-token token
                                         :query-params {"after" (first after)}}))]
    (js/console.log "Retreiving saved posts... from" after)
    (go
      (if @saved-for-reddit.core/get-posts?
        (let [response (<! saved-post-get-chan )
              posts (-> response :body :data :children)
              error (-> response :body :error)
              after-str (-> response :body :data :after)
              after-ret (if (clojure.string/blank? after-str) nil after-str)]
          (println "received response..." after-ret)
          (if (clojure.string/blank? error)
            (do
              #_(set-app-state-field :after after-ret)
              (doseq [p posts]
                (swap! saved-posts #(conj % %2) (repack-post (:data p))))
              (if (not (nil? after-ret))
                (get-all-saved-posts token username saved-posts after-ret)
                (update-view-after-retreive-complete)))
            (views/handle-error (str error " " (:error-text response) "\nYour API token might've expired"))))
        (update-view-after-retreive-complete)))))
