(ns saved-for-reddit.reddit-api
  (:require [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cemerick.url :as url])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def client-id "ZZ370hqcmUVsRQ")
(def redirect-uri "http://127.0.0.1:3449/")

(defn gen-auth-url []
  (let [cem-url (url/url "https://www.reddit.com/api/v1/authorize")
        query-param  {:client_id client-id
                      :response_type "code"
                      :state "abcdef"
                      :redirect_uri redirect-uri
                      :duration "temporary"
                      :scope "save"}]
    (str (assoc cem-url :query query-param))))

(defn make-remote-call [endpoint]
  (go (let [response (<! (http/get endpoint {:with-credentials? false}))]
        ;;enjoy your data
        (js/console.log (:body response)))))

(defn authorize []
  (go (let [response (<! (http/get "https://www.reddit.com/api/v1/authorize" {:query-params {:client_id client-id
                                                                                             :response_type "code"
                                                                                             :state "abcdef"
                                                                                             :redirect_uri redirect-uri
                                                                                             :duration "temporary"
                                                                                             :scope "save"}
                                                                              :with-credentials? false }))]
        (r/render-component response (.getElementById js/document "app")))))
