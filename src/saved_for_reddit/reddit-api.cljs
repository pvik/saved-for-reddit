(ns saved-for-reddit.reddit-api
  (:require [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cemerick.url :as url])
  (:require-macros [cljs.core.async.macros :refer [go]]))


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
                      :scope "save"}]
    (str (assoc cem-url :query query-param))))

(defn request-reddit-auth-token [code redirect-uri]
  (go (let [response (<! (http/post "https://www.reddit.com/api/v1/access_token" {:with-credentials? false
                                                                                  :form-params {:grant-type "authorization_code"
                                                                                                :redirect-uri redirect-uri
                                                                                                :code code}}))]
        ;;enjoy your data
        (js/console.log response))))
