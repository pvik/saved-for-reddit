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
        key (:key p)
        title (:title p)
        url (:url p)
        body (:body p)
        subreddit (:subreddit p)
        author (:author p)
        permalink (:permalink p)
        created-on-str (:created-on p)]
    [:div {:class "panel panel-default"}
     [:div {:class "panel-heading"}
      [:h4 {:class "panel-title"}
       [:a {:href url} title] " "
       [:a {:href (str "https://www.reddit.com/r/" subreddit)} [:span {:class "label label-default"} subreddit]]]
      [:small "submitted by " [:a {:href (str "https://www.reddit.com/user/" author)} [:small author]]
       " on " created-on-str]]
     [:div {:class "panel-body"}
      [:div {:dangerouslySetInnerHTML {:__html body}}]
      [:div {:class "btn-group btn-group-xs" :role= "group" :aria-label key}
       [:button {:type "button" :class "btn btn-default"
                 :on-click (fn [] (.open js/window permalink))} "Comments"]
       [:button {:type "button" :class "btn btn-default"} "Unsave"]]]]))

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
    [:span {:class "glyphicon glyphicon-refresh" :aria-hidden "true"}]]
   ])

(defn main-html [state posts]
  [:div {:class "col-md-10"}
   [:h4 "Saved Posts " [:span {:class "badge"} (count @posts)] ]
   [:div {:class "list-group"}
    (for [p @posts]
      [post-html p])]
   [:div {:class "row"}
    [:div {:class "col-md-12"}
     [:input {:type "button" :value "moar!"
              :id "btn-get-posts" :name "btn-get-posts"
              :on-click (fn [] (println "get more posts") (saved-for-reddit.reddit-api/get-saved-posts (:token @state) (:username @state) posts (:after @state)) )}]]]
   [:p "Reddit API Token: "
    [:input {:type "text" :id "token" :name "token"
             :value (:token @state) :readOnly "true"}]]])
