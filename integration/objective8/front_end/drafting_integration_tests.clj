(ns objective8.front-end.drafting-integration-tests
  (:require [midje.sweet :refer :all]
            [peridot.core :as p]
            [oauth.client :as oauth]
            [objective8.utils :as utils]
            [objective8.integration-helpers :as ih]
            [objective8.http-api :as http-api]
            [objective8.config :as config]))

(def TWITTER_ID "twitter-ID")
(def USER_ID 1)
(def OBJECTIVE_ID 2)
(def DRAFT_ID 3)

(def edit-draft-url (str utils/host-url "/objectives/" OBJECTIVE_ID "/edit-draft"))

(def user-session (ih/test-context))

(facts "about drafts" :integration
       (against-background
                 (oauth/access-token anything anything anything) => {:user_id TWITTER_ID}
                 (http-api/find-user-by-twitter-id anything) => {:status ::http-api/success
                                                                 :result {:_id USER_ID
                                                                          :username "username"}}
                 (http-api/get-user anything) => {:result {:writer-records [{:objective-id OBJECTIVE_ID}]}})

       (binding [config/enable-csrf false]
         (fact "writer for objective can view edit-draft page"
               (-> user-session
                   ih/sign-in-as-existing-user 
                   (p/request edit-draft-url)
                   (get-in [:response :status])) => 200) 

         (fact "writer can preview a draft"
               (let [{response :response} (-> user-session
                                              ih/sign-in-as-existing-user
                                              (p/request edit-draft-url
                                                         :request-method :post
                                                         :params {:action "preview"
                                                                  :content "A heading\n===\nSome content"}))]
                 (:status response) => 200
                 (:body response) => (contains "<h1>A heading</h1>")
                 (:body response) => (contains "Some content")))
         
         (fact "writer can submit a draft"
               (against-background
                 (http-api/create-draft anything) => {:status ::http-api/success
                                                      :result {:_id DRAFT_ID}})
               (let [{response :response} (-> user-session
                                              ih/sign-in-as-existing-user
                                              (p/request (str utils/host-url "/objectives/" OBJECTIVE_ID "/edit-draft")
                                                         :request-method :post
                                                         :params {:action "submit"
                                                                  :some :content}))]
                 (:headers response) => (ih/location-contains (str "/objectives/" OBJECTIVE_ID "/drafts/" DRAFT_ID))
                 (:status response) => 302))))