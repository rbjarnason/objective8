(ns objective8.db.storage-tests
  (:require [midje.sweet :refer :all]
            [korma.core :as korma]
            [objective8.storage.storage :as storage]
            [objective8.storage.database :as db]
            [objective8.storage.mappings :as m]))


(defn db-connection [] (db/connect! db/postgres-spec))

(defn truncate-tables []
  (korma/delete m/comment)
  (korma/delete m/objective)
  (korma/delete m/user))

(against-background
 [(before :contents (db-connection)) (after :facts (truncate-tables))]
 (facts "Storage tests" :integration
        (fact "a user entity can be stored in the database"
              (let [user {:entity :user :twitter-id "the-twitter-id" :foo "bar"}
                    store-result (storage/pg-store! user)
                    retrieve-result (storage/pg-retrieve {:entity :user :_id (:_id store-result)})]
                (first (:result retrieve-result))) => (contains {:twitter-id "the-twitter-id" :foo "bar"}))

        (fact "an objective entity can be stored in the database"
              (let [stored-user (storage/pg-store! {:entity :user
                                                    :twitter-id "twitter-TWITTER_ID"})
                    user-id (:_id stored-user)
                    objective {:entity :objective
                               :created-by-id user-id
                               :end-date "2015-01-01T00:00:00Z"
                               :description "description"
                               :goals "goals"
                               :title "title"}
                    store-result (storage/pg-store! objective)
                    retrieve-result (storage/pg-retrieve {:entity :objective :_id (:_id store-result)})]
                (first (:result retrieve-result)) => (contains {:created-by-id user-id
                                                                :end-date "2015-01-01T00:00:00Z"
                                                                :title "title"})))

        (fact "a comment entity can be stored in the database"
              (let [stored-user (storage/pg-store! {:entity :user
                                                    :twitter-id "twitter-TWITTER_ID"})
                    user-id (:_id stored-user)
                    stored-objective (storage/pg-store! {:entity :objective
                                                         :created-by-id user-id
                                                         :end-date "2015-01-01T00:00:00.000Z"})
                    objective-id (:_id stored-objective)
                    comment {:entity :comment
                             :created-by-id user-id
                             :objective-id objective-id
                             :comment "A comment"}
                    store-result (storage/pg-store! comment)
                    retrieve-result (storage/pg-retrieve {:entity :comment :_id (:_id store-result)})]
                (first (:result retrieve-result)) => (contains {:created-by-id user-id
                                                                :objective-id objective-id})))

        ;TODO - Look at Korma storgae.clj limiting results/sorting
        (fact "can limit the number of results when retrieving"
          (let [users [{:entity :user :twitter-id "the-twitter-id1"}
                       {:entity :user :twitter-id "the-twitter-id2"}
                       {:entity :user :twitter-id "the-twitter-id3"}]
                store-result (doall (map storage/pg-store! users))
                retrieve-result (storage/pg-retrieve {:entity :user} {:limit 2})]
            (count (:result retrieve-result)) => 2))))
