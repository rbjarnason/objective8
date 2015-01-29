(ns d-cent.front-end-integration-tests
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [peridot.core :as p]
            [oauth.client :as oauth]
            [d-cent.objectives :refer [request->objective]]
            [d-cent.storage :as storage]
            [d-cent.handlers.front-end :as front-end]
            [d-cent.api :as api]
            [d-cent.core :as core]))

(def the-user-id "twitter-user_id")
(def the-email-address "test@email.address.com")

(def objectives-create-request (mock/request :get "/objectives/create"))
(def objectives-post-request (mock/request :post "/objectives"))
(def objective-view-get-request (mock/request :get "/objectives/some-long-id"))
(def email-capture-get-request (mock/request :get "/email"))
(def user-profile-post-request (mock/request :post "/users"))

(def default-app (core/app core/app-config))

(def twitter-authentication-background
  (background (oauth/request-token anything anything) => {:oauth_token "the-token"}
              (oauth/access-token anything anything anything) => {:user_id the-user-id}))

(defn access-as-signed-in-user
  "Requires oauth/request-token and oauth/access-token to be stubbed in background or provided statements"
  [app url & args]
  (let [twitter-sign-in (-> (p/session app)
                            (p/request "/twitter-sign-in" :request-method :post)
                            (p/request "/twitter-callback?oauth_verifier=the-verifier"))]
    (apply p/request twitter-sign-in url args)))

(defn check-status [status]
  (fn [peridot-response]
    (= status (get-in peridot-response [:response :status]))))

(defn check-redirect-url [url]
  (fn [peridot-response]
    (= url (get-in peridot-response [:response :headers "Location"]))))

(facts "authorisation"
       (facts "signed in users"
              twitter-authentication-background
              (fact "can reach the create objective page"
                    (access-as-signed-in-user default-app "/objectives/create")
                    => (check-status 200))
              (fact "can post a new objective"
                    (access-as-signed-in-user default-app "/objectives" :request-method :post)
                    => (check-status 201)
                    (provided
                     (request->objective anything) => :an-objective
                     (storage/store! anything anything :an-objective) => :stored-objective))
              (fact "can reach the email capture page"
                    (access-as-signed-in-user default-app "/email")
                    => (check-status 200))
(facts "unauthorised users"
              (fact "cannot reach the objective creation page"
                    (default-app objectives-create-request)
                    => (contains {:status 302}))
              (fact "cannot post a new objective"
                    (default-app objectives-post-request)
                    => (contains {:status 401}))
              (fact "cannot reach the email capture page"
                    (default-app email-capture-get-request)
                    => (contains {:status 302}))
              (fact "cannot post their user profile"
                    (default-app user-profile-post-request)
                    => (contains {:status 302}))
              (fact "can access objective view"
                    (default-app objective-view-get-request)
                    => (contains {:status 200})
                    (provided
                      (storage/find-by anything anything "some-long-id") => :an-objective)))))


(fact "authorised user can post user profile to /users"
      twitter-authentication-background
      (let [params {:user-id the-user-id
                    :email-address the-email-address}]
        (access-as-signed-in-user default-app "/users" :request-method :post :params params)
      => (check-redirect-url "/users/some-id")
      (provided
        (api/post-user-profile params) => {:_id "some-id"} )))


(fact "authorised user can post and retrieve objective"
      twitter-authentication-background
      (let [store (atom {})
            app-config (into core/app-config {:store store})
            app (core/app app-config)
            params {:title "my objective title"
                    :goals "my objective goals"
                    :description "my objective description"
                    :end-date "my objective end-date"}]
        (do
          (access-as-signed-in-user app "/objectives" :request-method :post
                                    :params params)
          (storage/find-by store "objectives" #(= (:username %) the-user-id)))
        => (contains params)))