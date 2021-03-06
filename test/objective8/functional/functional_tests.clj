(ns objective8.functional.functional-tests 
  (:require [midje.sweet :refer :all]
            [org.httpkit.server :refer [run-server]]
            [clj-webdriver.taxi :as wd]
            [clj-webdriver.core :as wc]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [objective8.core :as core]
            [objective8.utils :as utils]
            [objective8.actions :as actions]
            [objective8.integration.integration-helpers :as integration-helpers]
            [dev-helpers.stub-twitter :refer [stub-twitter-auth-config twitter-id]]))

(def config-without-twitter (assoc core/app-config :authentication stub-twitter-auth-config))

(defn wait-for-title [title]
  (try 
    (wd/wait-until #(= (wd/title) title) 5000)
    (catch Exception e
      (prn (str ">>>>>>>>>>> Title never appeared:"))
      (prn (str "Expected: " title))
      (prn (str "Actual: " (wd/title)))
      (throw e))))

(def not-empty? (comp not empty?))

(defn wait-for-element [q]
  (try
    (wd/wait-until #(not-empty? (wd/elements q)) 5000)
    (catch Exception e
      (prn (str "Could not find element: " q))
      (throw e))))

(defn wait-for [pred]
  (try
    (wd/wait-until pred 5000)
    (catch Exception e
      (prn (str "Waiting for predicate failed"))
      (throw e))))

(def screenshot-directory "test/objective8/functional/screenshots")
(def screenshot-number (atom 0))
(defn screenshot [filename]
  (wd/take-screenshot :file (str screenshot-directory "/"
                                 (format "%02d" (swap! screenshot-number + 1))
                                 "_" filename ".png")))

(defn clear-screenshots []
  (doall (->> (io/file screenshot-directory)
              file-seq
              (filter #(re-matches #".*\.png$" (.getName %)))
              (map io/delete-file))))

(def journey-state (atom nil))

(def FIRST_DRAFT_MARKDOWN  "First draft heading\n===\n\n- Some content")
(def SECOND_DRAFT_MARKDOWN  "Second draft heading\n===\n\n- Some content\n- Some more content")
(def THIRD_DRAFT_MARKDOWN  "Third draft heading\n===\n\n- Some content\n- Some more content\n- Another line of content")


(against-background 
  [(before :contents (do (integration-helpers/db-connection)
                         (integration-helpers/truncate-tables)
                         (core/start-server config-without-twitter)
                         (wd/set-driver! {:browser :firefox})
                         (reset! journey-state {})
                         (clear-screenshots)))
   (after :contents (do (wd/quit)
                        (integration-helpers/truncate-tables)
                        (core/stop-server)))]
  (fact "can add an objective"
        (try (reset! twitter-id "twitter-OBJECTIVE_OWNER")
             (wd/to "localhost:8080")
             (wait-for-title "Objective[8]")
             (screenshot "home_page")

             (wd/click "a[href='/objectives']") 
             (wait-for-title "Objectives | Objective[8]")
             (screenshot "objectives_page")

             (wd/click "a[href='/objectives/create']") 
             (wait-for-title "Sign in or Sign up | Objective[8]")
             (screenshot "sign_in_page")

             (wd/click ".func--sign-in-with-twitter") 
             (wait-for-title "Sign up | Objective[8]")
             (screenshot "sign_up_almost_there")

             (wd/input-text "#username" "funcTestUser123")
             (-> "#email-address" 
                 (wd/input-text "func_test_user@domain.com")
                 wd/submit) 

             (screenshot "create_objective_page")
             (wait-for-title "Create an Objective | Objective[8]")

             (wd/input-text ".func--input-objective-title" "Functional test headline")
             (-> ".func--input-objective-background"
                 (wd/input-text 
                   "Functional test description with lots of hipster-ipsum:
                   Master cleanse squid nulla, ugh kitsch biodiesel cronut food truck. Nostrud Schlitz tempor farm-to-table skateboard, wayfarers adipisicing Pitchfork sunt Neutra brunch four dollar toast forage placeat. Fugiat lo-fi sed polaroid Portland et tofu Austin. Blue Bottle labore forage, in bitters incididunt ugh delectus seitan flannel. Mixtape migas cardigan, quis American Apparel culpa aliquip cupidatat et nisi scenester. Labore sriracha Etsy flannel XOXO. Normcore selvage do vero keytar synth.")
                 wd/submit) 

             (wait-for-title "Functional test headline | Objective[8]")
             (swap! journey-state assoc :objective-url (wd/current-url))
             (screenshot "objective_page")

             {:page-title (wd/title)
              :writer-name (wd/text ".func--writer-name")}

             (catch Exception e
               (screenshot "ERROR-Can-add-an-objective")
               (throw e)))
        =>  (contains {:page-title "Functional test headline | Objective[8]"
                       :writer-name "funcTestUser123"}))

  (fact "Can star an objective"
        (try (wd/to (:objective-url @journey-state))
             (wait-for-title "Functional test headline | Objective[8]")

             (wd/click ".func--objective-star")

             (wd/to "/")

             (wd/to (:objective-url @journey-state))
             (wait-for-title "Functional test headline | Objective[8]")

             (screenshot "objective_page_with_starred_objective")

             (wd/attribute ".func--objective-star" :class)

             (catch Exception e
               (screenshot "ERROR-Can-star-an-objective")
               (throw e)))
        => (contains "starred"))

  (fact "Can add a question"
        (try (wd/to "localhost:8080/objectives")
             (wait-for-title "Objectives | Objective[8]")
             (screenshot "objectives_page_with_an_objective")

             (wd/click ".func--objective-list-item-link")
             (wait-for-title "Functional test headline | Objective[8]")
             (screenshot "objective_page")

             (wd/click ".func--add-question")
             (wait-for-element ".func--question-textarea")
             (screenshot "add_question_page")

             (-> ".func--question-textarea"
                 (wd/input-text "Functional test question") 
                 (wd/submit)) 

             (wait-for-element ".func--add-question")
             (screenshot "objective_page_with_question")

             (catch Exception e
               (screenshot "Error-Can-add-questions")
               (throw e))))

(fact "Objective owner can promote and demote questions"
      (try (wd/to (:objective-url @journey-state))
           (wait-for-element ".func--promote-question")

           (wd/click ".func--promote-question")
           (wait-for-element ".func--demote-question")
           (screenshot "promoted_question")

           (wd/click ".func--demote-question")
           (wait-for-element ".func--promote-question")
           (screenshot "demoted_question")

           (catch Exception e
             (screenshot "Error-Objective-owner-can-promote-and-demote-questions")
             (throw e))))

(fact "Can answer a question"
      (try (wd/to (:objective-url @journey-state))
           (wait-for-element ".func--answer-link")

           (wd/click ".func--answer-link")
           (wait-for-element ".func--add-answer")

           (-> ".func--add-answer"
               (wd/input-text "Functional test answer") 
               (wd/submit))

           (wait-for-element ".func--answer-text")
           (screenshot "answered_question")
           (swap! journey-state assoc :question-url (wd/current-url))

           (wd/text ".func--answer-text")

           (catch Exception e
             (screenshot "Error-Can-answer-questions")
             (throw e)))
      => "Functional test answer")

(fact "Can up vote an answer" 
      (try (wd/to (:question-url @journey-state))

           (wait-for-element "textarea.func--add-answer")
           (wd/text ".func--up-score") => "0"

           (wd/click "button.func--up-vote")

           (wait-for-element "textarea.func--add-answer")
           (wd/text ".func--up-score") => "1"

           (catch Exception e
             (screenshot "Error-Can-vote-on-an-answer")
             (throw e))))

(fact "Can invite a writer"
      (try (wd/to (:objective-url @journey-state))
           (wait-for-title "Functional test headline | Objective[8]")
           (screenshot "objective_page")

           (wd/click ".func--invite-writer")
           (wait-for-element ".func--invite-writer")
           (screenshot "invite_writer_page")


           (wd/input-text ".func--writer-name" "Invitee name")
           (wd/input-text ".func--writer-email" "func_test_writer@domain.com")
           (-> ".func--writer-reason"
               (wd/input-text "Functional test invitation reason")
               wd/submit)
           (wait-for-title "Functional test headline | Objective[8]")
           (screenshot "objective_with_invitation_flash")

           (->> (wd/value ".func--invitation-url")
                (re-find #"http://.*$")
                (swap! journey-state assoc :invitation-url))
           {:page-title (wd/title)
            :flash-message (wd/text ".func--invitation-guidance")}

           (catch Exception e
             (screenshot "ERROR-Can-invite-a-writer")
             (throw e)))
      => (contains {:page-title "Functional test headline | Objective[8]"
                    :flash-message (contains "Your writer's invitation")}))

(fact "Can accept a writer invitation"
      (try (reset! twitter-id "FAKE_WRITER_ID")
           (wd/click ".func--masthead-sign-out") 
           (screenshot "after_sign_out")

           (wd/to (:invitation-url @journey-state))
           (wait-for-title "Functional test headline | Objective[8]")
           (screenshot "objective_page_after_hitting_invitation_url")

           (wd/click ".func--sign-in-to-accept")

           (wait-for-title "Sign in or Sign up | Objective[8]")
           (wd/click ".func--sign-in-with-twitter") 
           (wait-for-title "Sign up | Objective[8]")
           (wd/input-text "#username" "funcTestWriter") 
           (-> "#email-address" 
               (wd/input-text "func_test_invited_writer_user@domain.com")
               wd/submit) 

           (wait-for-title "Functional test headline | Objective[8]")
           (screenshot "objective_page_after_signing_up")

           (wd/click ".func--invitation-accept")

           (wait-for-title "Create profile | Objective[8]")
           (screenshot "create_profile_page")

           (wd/input-text ".func--name" "Invited writer real name") 
           (-> ".func--biog"
               (wd/input-text  "Biography with lots of text...")
               wd/submit)

           (wait-for-title "Functional test headline | Objective[8]")

           (screenshot "objective_page_from_recently_added_writer")

           (wd/text (second (wd/elements ".func--writer-name")))

           (catch Exception e
             (screenshot "ERROR-Can-accept-a-writer-invitation")
             (throw e)))
      => "Invited writer real name")

(fact "Can view writer profile page"
      (try (wd/to (:objective-url @journey-state))
           (wait-for-title "Functional test headline | Objective[8]")

           (wd/click (second (wd/elements ".func--writer-name")))
           (wait-for-title "Invited writer real name | Objective[8]")
           (screenshot "writer_profile_page")

           (wd/text (first (wd/elements ".func--writer-biog")))
           (catch Exception e
             (screenshot "ERROR-can-view-writer-profile")
             (throw e)))
      => "Biography with lots of text...")

(fact "Can edit writer profile"
      (try (wd/click ".func--edit-profile")
           (wait-for-title "Edit profile | Objective[8]")
           (screenshot "edit_profile_page")

           (-> ".func--name"
               wd/clear
               (wd/input-text "My new real name"))
           (-> ".func--biog"
               wd/clear
               (wd/input-text "My new biography") 
               wd/submit)

           (wait-for-title "My new real name | Objective[8]")
           (screenshot "updated_profile_page")

           {:name (wd/text (first (wd/elements ".func--writer-name")))
            :biog (wd/text (first (wd/elements ".func--writer-biog")))} 

           (catch Exception e
             (screenshot "ERROR-can-edit-writer-profile") 
             (throw e)))
      => (contains {:biog "My new biography"}))

(against-background
  [(before :contents (-> (:objective-url @journey-state)
                         (string/split #"/")
                         last
                         Integer/parseInt
                         actions/start-drafting!))]
  (fact "Can submit a draft"
        (try
          (wd/to (str (:objective-url @journey-state) "/drafts/latest"))
          (wait-for-title "Policy draft | Objective[8]")
          (screenshot "latest_draft_no_draft")

          (wd/click ".func--add-a-draft")
          (wait-for-title "Add draft | Objective[8]")
          (screenshot "add_draft_empty")

          (wd/input-text ".func--add-draft-content" FIRST_DRAFT_MARKDOWN)
          (wd/click ".func--preview-action")
          (wait-for-title "Add draft | Objective[8]")
          (screenshot "preview_draft")

          (wd/click ".func--submit-action")

          (wait-for-title "Policy draft | Objective[8]")
          (screenshot "submitted_draft")

          {:page-title (wd/title)
           :page-source (wd/page-source)}

          (catch Exception e
            (screenshot "ERROR-Can-submit-a-draft")
            (throw e)))
        => (contains {:page-title "Policy draft | Objective[8]"
                      :page-source (contains "First draft heading")}))

  (fact "Can view latest draft"
        (try
          (wd/to (:objective-url @journey-state))
          (wait-for-title "Functional test headline | Objective[8]")
          (screenshot "drafting_started_objective")

          (wd/click ".func--drafting-message-link")
          (wait-for-title "Drafts | Objective[8]")
          (screenshot "drafts_list_with_one_draft")

          (wd/click ".func--latest-draft-link")
          (wait-for-title "Policy draft | Objective[8]")
          (screenshot "latest_draft")

          {:page-title (wd/title)
           :page-source (wd/page-source)}

          (catch Exception e
            (screenshot "ERROR-Can-view-latest-draft")
            (throw e)))
        => (contains {:page-title "Policy draft | Objective[8]"
                      :page-source (contains "First draft heading")}))

  (fact "Can comment on a draft"
        (try
          (wd/to (str (:objective-url @journey-state) "/drafts/latest"))
          (wait-for-title "Policy draft | Objective[8]")

          (wd/input-text ".func--comment-form-text-area" "Functional test comment text")
          (wd/click ".func--comment-form-submit")
          (wait-for-title "Policy draft | Objective[8]")
          (screenshot "draft_with_comment")

          (wd/page-source)

          (catch Exception e
            (screenshot "ERROR-Can-comment-on-a-draft")
            (throw e)))
        => (contains "Functional test comment text"))

  (fact "Can down vote a comment on a draft"
        (try
          (wd/to (str (:objective-url @journey-state) "/drafts/latest"))
          (wait-for-title "Policy draft | Objective[8]")

          (wd/text ".func--down-score") => "0"

          (wd/click "button.func--down-vote")

          (wait-for-title "Policy draft | Objective[8]")
          (wd/text ".func--down-score") => "1"

          (catch Exception e
            (screenshot "ERROR-Can-vote-on-a-comment-on-a-draft")
            (throw e))))

  (fact "Can navigate between drafts"
        (try
          (wd/to (str (:objective-url @journey-state) "/drafts"))
          (wait-for-title "Drafts | Objective[8]")
          (screenshot "list_of_drafts_with_one_draft")

          (wd/click ".func--add-a-draft")
          (wait-for-title "Add draft | Objective[8]")
          (wd/input-text ".func--add-draft-content" SECOND_DRAFT_MARKDOWN)

          (wd/click ".func--submit-action")
          (wait-for-title "Policy draft | Objective[8]")
          (screenshot "second_draft")

          (wd/click ".func--add-a-draft")
          (wait-for-title "Add draft | Objective[8]")
          (wd/input-text ".func--add-draft-content" THIRD_DRAFT_MARKDOWN)

          (wd/click ".func--submit-action")
          (wait-for-title "Policy draft | Objective[8]")

          (wd/to (str (:objective-url @journey-state) "/drafts"))
          (wait-for-title "Drafts | Objective[8]")
          (screenshot "list_of_drafts_with_three_drafts")

          (wd/to (str (:objective-url @journey-state) "/drafts/latest"))
          (wait-for-title "Policy draft | Objective[8]")
          (screenshot "latest_draft_with_previous_button")

          (wd/click ".func--draft-version-previous-link")
          (wait-for-title "Policy draft | Objective[8]")
          (screenshot "second_draft_with_next")

          (wd/click ".func--draft-version-next-link")
          (wait-for-title "Policy draft | Objective[8]")
          (screenshot "third_draft")

          (swap! journey-state assoc :draft-url (wd/current-url))
          (swap! journey-state assoc :section-label (wd/attribute "h1" :data-section-label))

          (wd/page-source)
          (catch Exception e
            (screenshot "ERROR-Can-navigate-between-drafts")
            (throw e)))
        => (contains "Third draft heading"))

(fact "Can view draft diffs"
      (try
        (wd/click ".func--what-changed")
        (wait-for-title "Draft changes | Objective[8]")
        (screenshot "draft_diff")

        (catch Exception e
          (screenshot "ERROR-Can-view-draft-diffs") 
          (throw e))))

(fact "Can view draft section"
      (try
        (wd/click ".func--back-to-draft")
        (wait-for-title "Policy draft | Objective[8]")
        (wd/click ".func--annotation-link")
        (wait-for-title "Draft section | Objective[8]")
        (screenshot "draft_section")

        (wd/page-source) 
        (catch Exception e
          (screenshot "ERROR-Can-view-draft-section") 
          (throw e)))
      => (contains "Third draft heading"))

(fact "Can annotate a draft section"
      (try
        (wd/input-text ".func--comment-form-text-area" "my draft section annotation")
        (wd/click ".func--comment-form-submit")
        (wait-for-title "Draft section | Objective[8]")
        (screenshot "draft_section_with_comment")

        (wd/page-source)
        (catch Exception e
          (screenshot "ERROR-Can-annotate-a-draft-section")  
          (throw e)))
      => (contains "my draft section annotation"))

(fact "Can navigate to import from Google Drive"
      (try
        (wd/to (str (:objective-url @journey-state) "/drafts"))
        (wait-for-title "Drafts | Objective[8]")

        (wd/click ".func--import-draft-link")
        (wait-for-title "Import draft | Objective[8]")
        (screenshot "import_draft")

        (wd/click ".func--cancel-link")
        (wait-for-title "Drafts | Objective[8]")
        (screenshot "draft_list")

        (catch Exception e
          (screenshot "ERROR-Can-navigate-to-import-from-Google-Drive")
          (throw e)))))) 
