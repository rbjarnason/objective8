(ns objective8.integration.api.comments
  (:require [midje.sweet :refer :all]
            [peridot.core :as p]
            [cheshire.core :as json]
            [objective8.utils :as utils]
            [objective8.core :as core]
            [objective8.integration.integration-helpers :as helpers]
            [objective8.integration.storage-helpers :as sh]
            [objective8.middleware :as m]
            [objective8.comments :as comments]))

(def app (helpers/test-context))

(def OBJECTIVE_ID 234)
(def USER_ID 1)

(def the-comment {:comment "The comment"
                  :objective-id OBJECTIVE_ID
                  :created-by-id USER_ID})

(def the-comment-as-json (json/generate-string the-comment))
(def the-invalid-comment {:comment "The comment"
                          :objective-id OBJECTIVE_ID })

(defn a-comment [comment-on-id objective-id created-by-id]
  {:comment "The comment"
   :objective-id objective-id
   :comment-on-id comment-on-id
   :created-by-id created-by-id})

(facts "POST /api/v1/meta/comments"
       (against-background
        (m/valid-credentials? anything anything anything) => true)
       (against-background
        [(before :contents (do (helpers/db-connection)
                               (helpers/truncate-tables)))
         (after :facts (helpers/truncate-tables))]
        (fact "the posted comment is stored"
              (let [{user-id :_id :as user} (sh/store-a-user)
                    {o-id :objective-id d-id :_id global-id :global-id} (sh/store-a-draft)
                    uri-for-draft (str "/objectives/" o-id "/drafts/" d-id)
                    comment-data {:comment-on-uri uri-for-draft
                                  :comment "A comment"
                                  :created-by-id user-id}
                    {response :response} (p/request app (str "/api/v1/meta/comments")
                                                    :request-method :post
                                                    :content-type "application/json"
                                                    :body (json/generate-string comment-data))]
                (:status response) => 201
                (:body response) => (helpers/json-contains {:_id integer?
                                                            :uri (contains "/comments/")
                                                            :comment-on-uri uri-for-draft
                                                            :comment "A comment"
                                                            :created-by-id user-id})
                (:body response) =not=> (helpers/json-contains {:comment-on-id anything})
                (:body response) =not=> (helpers/json-contains {:global-id anything})
                (:headers response) => (helpers/location-contains (str "/api/v1/meta/comments/"))))

        (fact "returns 404 when entity to be commented on doesn't exist"
              (let [comment-data {:comment-on-uri "nonexistent/entity"
                                  :comment "A comment"
                                  :created-by-id 1}
                    {response :response} (p/request app (str "/api/v1/meta/comments")
                                                    :request-method :post
                                                    :content-type "application/json"
                                                    :body (json/generate-string comment-data))]
                (:status response) => 404
                (:body response) => (helpers/json-contains {:reason "Entity does not exist"})))))

(facts "GET /api/v1/meta/comments?uri=<uri>"
       (against-background
        [(before :contents (do (helpers/db-connection)
                               (helpers/truncate-tables)))
         (after :facts (helpers/truncate-tables))]

        (fact "retrieves comments for the entity at <uri>"
              (let [user (sh/store-a-user)
                    {draft-id :_id objective-id :objective-id :as draft} (sh/store-a-draft)
                    draft-uri (str "/objectives/" objective-id "/drafts/" draft-id)
                    stored-comments (doall (->> (repeat {:entity draft :user user})
                                                (take 5)
                                                (map sh/store-a-comment)
                                                (map #(dissoc % :global-id :comment-on-id))
                                                (map #(assoc % :comment-on-uri draft-uri
                                                             :uri (str "/comments/" (:_id %))))))
                    escaped-draft-uri (str "%2fobjectives%2f" objective-id "%2fdrafts%2f" draft-id)
                    {response :response} (p/request app (str "/api/v1/meta/comments?uri=" escaped-draft-uri))]
                (:body response) => (helpers/json-contains (map contains (reverse stored-comments)))))

        (fact "returns 404 if the entity to retrieve comments for does not exist"
              (let [{response :response} (p/request app (str "/api/v1/meta/comments?uri=" "%2fnonexistent%2furi"))]
                (:status response) => 404
                (:body response) => (helpers/json-contains {:reason "Entity does not exist"})))))

(facts "GET /api/v1/meta/comments?uri=<uri>&sorted-by=<sorting type>"
       (against-background
        [(before :contents (do (helpers/db-connection)
                               (helpers/truncate-tables)))
         (after :facts (helpers/truncate-tables))]

        (fact "retrieves comments sorted by number of up-votes when sorting type is 'up-votes'"
              (let [objective (sh/store-an-open-objective)
                    objective-uri (str "/objectives/" (:_id objective))

                    comment-with-most-votes (sh/store-a-comment {:entity objective})
                    _ (sh/store-an-up-down-vote (:global-id comment-with-most-votes) :up)
                    _ (sh/store-an-up-down-vote (:global-id comment-with-most-votes) :up)

                    comment-with-least-votes (sh/store-a-comment {:entity objective})

                    comment-with-some-votes (sh/store-a-comment {:entity objective})
                    _ (sh/store-an-up-down-vote (:global-id comment-with-some-votes) :up)

                    escaped-objective-url (str "%2Fobjectives%2F" (:_id objective))
                    {body :body} (:response (p/request app (str (utils/path-for :api/get-comments)
                                                                "?uri=" escaped-objective-url
                                                                "&sorted-by=up-votes")))]
                body => (helpers/json-contains [(contains {:_id (:_id comment-with-most-votes)})
                                                (contains {:_id (:_id comment-with-some-votes)})
                                                (contains {:_id (:_id comment-with-least-votes)})])))

        (fact "retrieves comments sorted by number of down-votes when sorting type is 'down-votes'")))
