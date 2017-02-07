(ns saved-for-reddit.redditapi
  (:require [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! >! chan]]
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
  (println (str "using token " (:token @saved-for-reddit.core/app-state)))
  (go
    (let [response (<! (http/post "https://oauth.reddit.com/api/unsave"
                                  {:with-credentials? false
                                   :oauth-token (:token @saved-for-reddit.core/app-state)
                                   :form-params {:id thing-id}}))
          status (:status response)
          body   (:body response)
          error  (:error body)]
      (if (clojure.string/blank? error)
        (do
          (println body)
          ;; set the post visibility to false
          (swap! saved-for-reddit.core/saved-posts
                 update-in
                 [(keyword thing-id)]
                 #(assoc % :visible? false))
          )
        (views/handle-error error)))))

(defn repack-post [p]
  (let [id (:id p)
        visible? true
        name (:name p)
        link? (nil? (:title p))
        key (str (:name p) (:subreddit_id p) (:link_id p))
        title (unescapeEntities (if link? (:link_title p) (:title p)))
        url (if link? (str (:link_url p) key) (:url p))
        body (if (not (nil? (:body_html p))) (unescapeEntities (:body_html p)))
        subreddit (:subreddit p)
        author (:author p)
        permalink (if link? (:link_url p) (str "https://www.reddit.com" (:permalink p)))
        nsfw? (:over_18 p)
        thumbnail (:thumbnail p)
        num_comments (:num_comments p)
        created-on-epoch-local (+ (:created_utc p) (* 3600 (.getTimezoneOffset (js/Date.))))
        created-on-str (timef/unparse time-formatter (timec/from-long (* 1000 created-on-epoch-local)))]
    ;; update the subreddit atom
    (swap! saved-for-reddit.core/subreddits update-in [(keyword subreddit)]
           (fn [sr-map] (assoc (assoc sr-map :count (inc (:count sr-map))) :filtered? false)))
    {:id id :visible? visible? :name name :link link? :key key :title title
     :url url :body body :subreddit subreddit :author author
     :nsfw? nsfw? :thumbnail thumbnail :num_comments num_comments
     :permalink permalink :created-on created-on-str}))

(defn get-saved-posts [token username saved-posts retreive-all? after callback]
  (let [saved-post-get-chan (if (nil? after)
                              (http/get (str "https://oauth.reddit.com/user/" username "/saved")
                                        {:with-credentials? false
                                         :oauth-token token})
                              (http/get (str "https://oauth.reddit.com/user/" username "/saved")
                                        {:with-credentials? false
                                         :oauth-token token
                                         :query-params {"after" after}}))]
    (js/console.log "Retreiving saved posts..." after)
    (go (let [response (<! saved-post-get-chan )
              posts (-> response :body :data :children)
              error (-> response :body :error)
              after-str (-> response :body :data :after)
              after-ret (if (clojure.string/blank? after-str) nil after-str)]
          (if (clojure.string/blank? error)
            (do
              (println "received response .. after " after-ret)
              ;; add the retreived posts to posts atom
              (doseq [p posts]
                (let [repacked-post  (repack-post (:data p))]
                  (swap! saved-posts update-in [(keyword (:name repacked-post))] (fn [arg1] repacked-post))))
              (set-app-state-field :after after-ret)
              (if (nil? after-ret)
                ;; no more pages of saved posts
                (if (not (nil? callback))
                  (>! callback "-"))
                ;; (set! (.-disabled (dommy/sel1 :#btn-get-posts)) true)
                ;; more pages exists; should we retreive?
                (if retreive-all?
                  (get-saved-posts token username saved-posts true after-ret callback)))
              after-ret)
            (views/handle-error (str error " " (:error-text response) "\nYour API token might've expired")))))))

(defn update-view-after-retreive-complete []
  (set! ( .-visibility (.-style (dommy/sel1 :#btn-stop-get-posts))) "hidden")
  (set! ( .-visibility (.-style (dommy/sel1 :#loading-gif))) "hidden")
  (set! (.-disabled (dommy/sel1 :#btn-search-posts)) true)
  (set! (.-disabled (dommy/sel1 :#txt-search-posts)) true)
  (set! (.-placeholder (dommy/sel1 :#txt-search-posts)) "Search...")
  (saved-for-reddit.core/gen-csv-string))

(defn get-all-saved-posts [token username saved-posts]
  (let [callback (chan)]
    (get-saved-posts token username saved-posts true nil callback)
    (go
      (let [resp (<! callback)]
        (println "received callback from get-saved-posts")
        (update-view-after-retreive-complete)))))

(defn update-all-posts-visibility-true []
  (let [posts saved-for-reddit.core/saved-posts
        subreddits saved-for-reddit.core/subreddits]
    (saved-for-reddit.core/console-log "updating posts visiblity by subreddit filter")
    (doseq [post-name-keyword (vec (keys @posts))]
      (let [post (post-name-keyword @posts)]
        (swap! posts update-in [post-name-keyword]
               (fn [p-map] (assoc p-map :visible? true)))))))


(defn update-all-posts-visibility-by-subreddit-filter []
  (let [posts saved-for-reddit.core/saved-posts
        subreddits saved-for-reddit.core/subreddits]
    (saved-for-reddit.core/console-log "updating posts visiblity by subreddit filter")
    (doseq [post-name-keyword (vec (keys @posts))]
      (let [post (post-name-keyword @posts)
            subreddit-filtered? (:filtered? ((keyword (:subreddit post)) @subreddits)) ]
        (swap! posts update-in [post-name-keyword]
               (fn [p-map] (assoc p-map :visible? subreddit-filtered?)))))))

(defn filter-subreddit [s]
  (let [subreddits saved-for-reddit.core/subreddits]
    (saved-for-reddit.core/console-log "filtering " s)
    (swap! subreddits update-in [s]
           (fn [sr-map] (assoc sr-map :filtered? true))))
  (reset! saved-for-reddit.core/display-subreddit-filter-btn? true)
  (update-all-posts-visibility-by-subreddit-filter))

(defn unfilter-subreddit [s]
  (let [subreddits saved-for-reddit.core/subreddits]
    (saved-for-reddit.core/console-log "unfiltering " s)
    (swap! subreddits update-in [s]
           (fn [sr-map] (assoc sr-map :filtered? false))))
  (update-all-posts-visibility-by-subreddit-filter))

(defn clear-subreddit-filter []
  (let [subreddits saved-for-reddit.core/subreddits]
    (saved-for-reddit.core/console-log "updating posts visiblity by subreddit filter")
    (doseq [s (vec (keys @subreddits))]
      (swap! subreddits update-in [s]
             (fn [sr-map] (assoc sr-map :filtered? false)))))
  (reset! saved-for-reddit.core/display-subreddit-filter-btn? false)
  (update-all-posts-visibility-true))
