(ns saved-for-reddit.views
  (:require [reagent.core :as r]
            [dommy.core :as dommy]))

(defn handle-error [error]
  (r/render-component [error-html error] (dommy/sel1 :#error)))

(defn error-html [error]
  [:div {:class "alert alert-danger" :role "alert"}
   [:pre error]
   [:input {:type "button" :value "Clear app LocalStorage and refresh!"
            :on-click (fn [] (alandipert.storage-atom/remove-local-storage! :saved-for-reddit-app-state)
                        (set! (.-location js/window) "/"))}]])

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
       [:a {:href (str "https://www.reddit.com/r/" subreddit)} [:span {:class "badge"} subreddit]]]
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
