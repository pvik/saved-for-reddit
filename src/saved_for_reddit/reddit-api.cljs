(ns saved-for-reddit.reddit-api
  (:require [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cemerick.url :as url]
            [cljs-time.coerce :as timec]
            [cljs-time.format :as timef]
            [goog.string :refer [unescapeEntities]] )
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; a global time-formatter to handle how time strings are displayed
(def time-formatter (timef/formatter "yyyy-MM-dd HH:mm"))

(defn make-remote-get-call [endpoint]
  (go (let [response (<! (http/get endpoint {:with-credentials? false}))]
        ;;enjoy your data
        (js/console.log (:body response)))))

(defn make-remote-post-call [endpoint]
  (go (let [response (<! (http/post endpoint {:with-credentials? false}))]
        ;;enjoy your data
        (js/console.log (:body response)))))

(defn gen-reddit-auth-url [client-id redirect-uri state]
  (let [cem-url (url/url "https://www.reddit.com/api/v1/authorize")
        query-param  {:client_id client-id
                      :response_type "code"
                      :state state
                      :redirect_uri redirect-uri
                      :duration "temporary"
                      :scope "history,identity,save"}]
    (str (assoc cem-url :query query-param))))

(defn repack-post [p]
  (let [link? (nil? (:title p))
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
