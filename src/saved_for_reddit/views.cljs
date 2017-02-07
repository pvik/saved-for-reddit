(ns saved-for-reddit.views
  (:require [reagent.core :as r]
            [dommy.core :as dommy]))

(defn error-html [error]
  [:div {:class "alert alert-danger" :role "alert"}
   [:pre error]
   [:input {:type "button" :value "Clear app LocalStorage and reload!"
            :on-click #(saved-for-reddit.core/clear-and-refresh-app)}]])

(defn handle-error [error]
  (r/render-component [error-html error] (dommy/sel1 :#error)))

(defn post-html [p]
  (let [link? (:link p)
        visible? (:visible? p)
        key (:key p)
        name (:name p)
        title (:title p)
        url (:url p)
        body (:body p)
        subreddit (:subreddit p)
        author (:author p)
        permalink (:permalink p)
        created-on-str (:created-on p)
        nsfw? (:nsfw? p)
        thumbnail (:thumbnail p)
        num_comments (:num_comments p)]
    (if visible?
      [:div {:class "panel panel-default"}
       [:div {:class "media"}
        (if (not (or (= thumbnail "self") (= thumbnail "image") (= thumbnail "default")))
          (if (= thumbnail "nsfw")
            [:div {:class "media-left"}
             [:img {:class "media-object" :height "70em" :src "img/nsfw.png"}]]
            [:div {:class "media-left"}
             [:img {:class "media-object" :height "80em" :src thumbnail}]]))
        [:div {:class "media-body"}
         [:h4 {:class "media-heading"}
          [:a {:href url} title] " "]
         [:a {:href (str "https://www.reddit.com/r/" subreddit)} [:span {:class "label label-default"} subreddit]]
         (if nsfw?
           [:span {:class "label label-danger"} "NSFW"])
         [:br]
         [:small "submitted by " [:a {:href (str "https://www.reddit.com/user/" author)} [:small author]]
          " on " created-on-str]
         [:br]
         [:div {:dangerouslySetInnerHTML {:__html body}}]
         ]]
       [:div {:class "panel-heading"}
        [:div {:class "btn-group btn-group-xs" :role= "group" :aria-label key}
         [:button {:type "button" :class "btn btn-default"
                   :on-click (fn [] (.open js/window permalink))} "Comments " [:span {:class "badge"} num_comments]]
         [:button {:type "button" :class "btn btn-default"
                   :on-click (fn [] (saved-for-reddit.redditapi/reddit-unsave name))} "Unsave"]]]])))

(defn subreddit-html [subreddits]
  [:div {:class "list-group"}
   (if @saved-for-reddit.core/display-subreddit-filter-btn?
     ^{:key (keyword "clear-subreddit-filter")}
     [:button {:type "button"
               :on-click (fn [] (.log js/console "clicked on clear subreddit filter")
                           (saved-for-reddit.redditapi/clear-subreddit-filter))
               :class "list-group-item list-group-item-warning"} "Clear Subreddit Filtering"])
   (doall
    (for [s (vec (keys @subreddits))]
      (if (:filtered? (s @subreddits))
        ^{:key s}
        [:button {:type "button "
                  :on-click (fn [] (.log js/console "clicked on " s)
                              (saved-for-reddit.redditapi/unfilter-subreddit s))
                  :class "list-group-item list-group-item-info"} s [:span {:class "badge"} (:count (s @subreddits))]]
        ^{:key s}
        [:button {:type "button "
                  :on-click (fn [] (.log js/console "clicked on " s)
                              (saved-for-reddit.redditapi/filter-subreddit s))
                  :class "list-group-item"} s [:span {:class "badge"} (:count (s @subreddits))]])))])

(defn loggedin-html [user-name]
  [:p {:class "navbar-text navbar-right"} "Logged in as " user-name])

(defn search-bar-html []
  [:form {:class "navbar-form navbar-left" :role "search"}
   [:div {:class "form-group"}
    [:input {:id "txt-search-posts" :type "text" :class "form-control" :placeholder "Loading..." :disabled true}]]
   [:button {:id "btn-search-posts" :type "submit" :class "btn btn-primary" :disabled true}
    [:span {:class "glyphicon glyphicon-search" :aria-hidden "true"}]]
   [:button {:id "btn-refresh" :type "submit" :class "btn btn-primary"
             :on-click #(saved-for-reddit.core/clear-and-refresh-app)}
    [:span {:class "glyphicon glyphicon-refresh" :aria-hidden "true"}]  ]
   ])

(defn main-html [state posts subreddits]
  [:div {:class "col-md-10"}
   [:h4
    [:img {:id "loading-gif" :src "img/loading.gif"}]
    " Saved Posts "
    [:span {:class "badge"} (count @posts)] " "
    [:button {:id "btn-stop-get-posts" :type "submit" :class "btn btn-primary btn-xs"
              :on-click (fn []
                          (swap! saved-for-reddit.core/get-posts? not))}
     [:span {:class "glyphicon glyphicon-ban-circle" :aria-hidden "true"}]]
    [:a {:target "_blank" :download "reddit-saved-posts.csv"
         :href (str "data:attachment/csv," @saved-for-reddit.core/csv-string)  }
     [:span {:class "badge"} "export as csv"]]
    ]
   [:div {:class "row"}
    [:div {:class "col-md-9"}
     [:div {:class "list-group"}
      (doall
       (for [post-name (vec (keys @posts))]
         (if (:visible? (post-name @posts))
           ^{:key post-name}
           [post-html (post-name @posts)])))]]
    [:div {:class "col-md-3"}
     [:h4 "Subreddits"]
     [subreddit-html subreddits]]]
   [:div {:class "row"}
    [:div {:class "col-md-12"}
     [:input {:type "button" :value "moar!"
              :id "btn-get-posts" :name "btn-get-posts"
              :on-click (fn []
                          (println "get more posts")
                          (saved-for-reddit.redditapi/get-saved-posts (:token @state) (:username @state) posts false (:after @state) nil) )}]]]
   [:p "Reddit API Token: "
    [:input {:type "text" :id "token" :name "token"
             :value (:token @state) :readOnly "true"}]]] )
