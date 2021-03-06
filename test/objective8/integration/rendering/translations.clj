(ns objective8.integration.rendering.translations
  (:require [midje.sweet :refer :all]
            [peridot.core :as p]
            [oauth.client :as oauth]
            [endophile.core :as ec]
            [endophile.hiccup :as eh]
            [hiccup.core :as hc]
            [objective8.handlers.front-end :as fe]
            [objective8.utils :as utils]
            [objective8.http-api :as http-api]
            [objective8.integration.integration-helpers :as helpers]))

(def OBJECTIVE_ID 1)
(def QUESTION_ID 2)
(def QUESTION_URI (str "/objectives/" OBJECTIVE_ID "/questions/" QUESTION_ID))
(def USER_ID 3)
(def DRAFT_ID 4)
(def UUID "random-uuid")
(def INVITATION_ID 3)
(def OBJECTIVE_TITLE "some title")
(def ACTIVE_INVITATION {:_id INVITATION_ID
                        :invited-by-id USER_ID
                        :objective-id OBJECTIVE_ID
                        :uuid UUID
                        :status "active"})
(def INVITATION_URL (utils/path-for :fe/writer-invitation :uuid UUID))
(def SOME_MARKDOWN  "A heading\n===\nSome content")
(def SOME_HICCUP (eh/to-hiccup (ec/mp SOME_MARKDOWN)))
(def SOME_HTML (hc/html SOME_HICCUP))

(def user-session (helpers/test-context))

(facts "about rendering index page"
       (fact "there are no untranslated strings"
             (let [{status :status body :body} (-> user-session
                                                   (p/request (utils/path-for :fe/index))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(facts "about rendering learn-more page"
       (fact "there are no untranslated strings"
             (let [{status :status body :body} (-> user-session
                                                   (p/request (utils/path-for :fe/learn-more))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(facts "about rendering project-status page"
       (fact "there are no untranslated strings"
             (let [{status :status body :body} (-> user-session
                                                   (p/request (utils/path-for :fe/project-status))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(def drafting-objective {:_id OBJECTIVE_ID
                         :title "my objective title"
                         :description "my objective description"
                         :end-date (utils/string->date-time "2012-12-12")
                         :username "Barry"
                         :uri (str "/objectives/" OBJECTIVE_ID)
                         :status "drafting"})

(def open-objective (assoc drafting-objective :status "open" 
                           :end-date (utils/date-time->date-time-plus-30-days (utils/current-time))))


(facts "about rendering objective-list page"
       (fact "there are no untranslated strings"
             (against-background
               (http-api/get-objectives) => {:status ::http-api/success 
                                             :result [drafting-objective open-objective]})
             (let [{status :status body :body} (-> user-session
                                                   (p/request (utils/path-for :fe/objective-list))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(facts "about rendering create-objective page"
       (fact "there are no untranslated strings"
             (against-background
               (oauth/access-token anything anything anything) => {:user_id "TWITTER_ID"}
               (http-api/find-user-by-twitter-id anything) => {:status ::http-api/success
                                                               :result {:_id USER_ID
                                                                        :username "username"}})
             (let [{status :status body :body} (-> user-session
                            (helpers/sign-in-as-existing-user)
                            (p/request (utils/path-for :fe/create-objective-form))
                            :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(facts "about rendering objective page"
       (fact "there are no untranslated strings"
             (against-background 
               (http-api/get-objective OBJECTIVE_ID) => {:status ::http-api/success
                                                         :result open-objective}
               (http-api/get-comments anything)=> {:status ::http-api/success :result []}
               (http-api/retrieve-writers OBJECTIVE_ID) => {:status ::http-api/success :result []}
               (http-api/retrieve-questions OBJECTIVE_ID) => {:status ::http-api/success :result []}) 
             (let [{status :status body :body} (-> user-session
                                                   (p/request (utils/path-for :fe/objective :id OBJECTIVE_ID))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(def a-question {:question "The meaning of life?"
                 :created-by-id USER_ID
                 :uri QUESTION_URI
                 :objective-id OBJECTIVE_ID
                 :_id QUESTION_ID})

(facts "about rendering question page"
       (fact "there are no untranslated strings"
             (against-background 
               (http-api/get-objective OBJECTIVE_ID) => {:status ::http-api/success
                                                         :result open-objective}
               (http-api/get-question OBJECTIVE_ID QUESTION_ID) => {:status ::http-api/success 
                                                                    :result a-question}
               (http-api/retrieve-answers QUESTION_URI) => {:status ::http-api/success
                                                            :result []})
             (let [{status :status body :body} (-> user-session
                                                   (p/request (utils/path-for :fe/question 
                                                                              :id OBJECTIVE_ID
                                                                              :q-id QUESTION_ID))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(facts "about rendering add-question page"
       (fact "there are no untranslated strings"
             (against-background
               (oauth/access-token anything anything anything) => {:user_id "TWITTER_ID"}
               (http-api/find-user-by-twitter-id anything) => {:status ::http-api/success
                                                               :result {:_id USER_ID
                                                                        :username "username"}}
               (http-api/get-objective OBJECTIVE_ID) => {:status ::http-api/success
                                                        :result open-objective})
             (let [{status :status body :body} (-> user-session
                                                   (helpers/sign-in-as-existing-user)
                                                   (p/request (utils/path-for :fe/add-a-question :id OBJECTIVE_ID))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(facts "about rendering invite-writer page"
       (fact "there are no untranslated strings"
             (against-background
               (oauth/access-token anything anything anything) => {:user_id "TWITTER_ID"}
               (http-api/find-user-by-twitter-id anything) => {:status ::http-api/success
                                                               :result {:_id USER_ID
                                                                        :username "username"}}
               (http-api/get-user anything) => {:result {:writer-records [{:objective-id OBJECTIVE_ID}]}}
               (http-api/get-objective OBJECTIVE_ID) => {:status ::http-api/success
                                                        :result open-objective})
             (let [{status :status body :body} (-> user-session
                                                   (helpers/sign-in-as-existing-user)
                                                   (p/request (utils/path-for :fe/invite-writer :id OBJECTIVE_ID))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(facts "about rendering create-profile page"
       (fact "there are no untranslated strings"
             (against-background
               (oauth/access-token anything anything anything) => {:user_id "TWITTER_ID"}
               (http-api/find-user-by-twitter-id anything) => {:status ::http-api/success
                                                               :result {:_id USER_ID
                                                                        :username "username"}}
               (http-api/retrieve-invitation-by-uuid UUID) => {:status ::http-api/success
                                                               :result ACTIVE_INVITATION}
               (http-api/get-objective OBJECTIVE_ID anything) => {:status ::http-api/success
                                                                  :result {:title OBJECTIVE_TITLE}})
             (let [{status :status body :body} (-> user-session
                                                   helpers/sign-in-as-existing-user
                                                   (p/request INVITATION_URL)
                                                   (p/request (utils/path-for :fe/create-profile-get))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(facts "about rendering edit-profile page"
       (fact "there are no untranslated strings"
             (against-background
               (oauth/access-token anything anything anything) => {:user_id "TWITTER_ID"}
               (http-api/find-user-by-twitter-id anything) => {:status ::http-api/success
                                                               :result {:_id USER_ID
                                                                        :username "username"}}
               (http-api/get-user anything) => {:status ::http-api/success
                                                :result {:writer-records [{:objective-id OBJECTIVE_ID}]
                                                         :username "username"
                                                         :profile {:name "Barry"
                                                                   :biog "I'm Barry..."}}})
             (let [{status :status body :body} (-> user-session 
                                                   helpers/sign-in-as-existing-user
                                                   (p/request (utils/path-for :fe/edit-profile-get))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(facts "about rendering the writer-dashboard pages"
       (against-background
        (oauth/access-token anything anything anything) => {:user_id "TWITTER_ID"}
        (http-api/find-user-by-twitter-id anything) => {:status ::http-api/success
                                                        :result {:_id USER_ID
                                                                 :username "username"}}
        (http-api/get-user anything) => {:status ::http-api/success
                                         :result {:username "username"
                                                  :writer-records [{:objective-id OBJECTIVE_ID}]}})

       (facts "about the questions dashboard"
              (fact "there are no untranslated strings"
                    (against-background
                     (http-api/get-objective anything) => {:status ::http-api/success
                                                                    :result (assoc open-objective :meta {:stars 1})}
                     (http-api/retrieve-questions anything anything) => {:status ::http-api/success
                                                                         :result []}
                     (http-api/retrieve-answers anything anything) => {:status ::http-api/success
                                                                       :result []})
                    (let [{status :status body :body} (-> user-session
                                                          helpers/sign-in-as-existing-user
                                                          (p/request (utils/path-for :fe/dashboard-questions :id OBJECTIVE_ID))
                                                          :response)]
                      status => 200
                      body => helpers/no-untranslated-strings)))

       (facts "about the comments dashboard"
              (fact "there are no untranslated strings"
                    (against-background
                     (http-api/get-objective anything) => {:status ::http-api/success
                                                                    :result (assoc open-objective :meta {:stars 1})}
                     (http-api/get-all-drafts anything) => {:status ::http-api/success
                                                            :result []}
                     (http-api/get-comments anything anything) => {:status ::http-api/success
                                                                   :result []})
                    (let [{status :status body :body} (-> user-session
                                                          helpers/sign-in-as-existing-user
                                                          (p/request (utils/path-for :fe/dashboard-comments :id OBJECTIVE_ID))
                                                          :response)]
                      status => 200
                      body => helpers/no-untranslated-strings))))

(def CREATED_AT "2015-04-20T10:31:17.343Z")

(facts "about rendering profile page"
       (fact "there are no untranslated strings"
             (against-background
               (http-api/find-user-by-username "username") => {:status ::http-api/success
                                                               :result {:username "username"
                                                                        :_created_at CREATED_AT
                                                                        :profile {:name "Barry"
                                                                                  :biog "I'm Barry..."}}})
             (let [{status :status body :body} (-> user-session 
                                                   (p/request (utils/path-for :fe/profile :username "username"))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))


(facts "about rendering sign-in page"
       (fact "there are no untranslated strings"
             (let [{status :status body :body} (-> user-session
                                                   (p/request (utils/path-for :fe/sign-in))
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(def twitter-callback-url (str utils/host-url "/twitter-callback?oauth_verifier=VERIFICATION_TOKEN"))

(facts "about rendering sign-up page"
       (fact "there are no untranslated strings"
             (against-background
               (oauth/access-token anything anything anything) => {:user_id "TWITTER_ID"}
               (http-api/find-user-by-twitter-id "twitter-TWITTER_ID") => {:status ::http-api/not-found})
             (let [{status :status body :body} (-> user-session
                                                   (p/request twitter-callback-url)
                                                   p/follow-redirect
                                                   :response)]
               status => 200
               body => helpers/no-untranslated-strings)))

(facts "about rendering error-404 page"
       (fact "there are no untranslated strings"
             (let [{status :status body :body} (-> user-session
                                                   (p/request (str utils/host-url "/INVALID_ROUTE"))
                                                   :response)]
               status => 404
               body => helpers/no-untranslated-strings)))
