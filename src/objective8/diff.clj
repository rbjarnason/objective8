(ns objective8.diff
  (:require [diff-match-patch-clj.core :as dmp]
            [net.cgrand.enlive-html :as html]
            [cljsoup.core :as soup-core]
            [cljsoup.nodes :as soup-nodes]
            [jsoup.soup :as jsoup]
            [objective8.utils :as utils]
            [objective8.drafts :as drafts]))

(defn get-delete [diff]
  (when (= "DELETE" (str (. diff operation)))
    (. diff text)))

(defn get-insert [diff]
  (when (= "INSERT" (str (. diff operation)))
    (. diff text)))

(defn get-third-element-if-exists [elements]
  (if (> (count elements) 2) (nth elements 2) " "))

(defn get-text [hiccup-draft]
  (clojure.string/join (map get-third-element-if-exists hiccup-draft)))

(defn remove-tags [hiccup-draft]
  (let [html-draft (utils/hiccup->html hiccup-draft)
        parsed-draft (soup-core/from-string html-draft)
        body (soup-nodes/body parsed-draft)]
    (prn (html/text (soup-nodes/select parsed-draft "body > p")))))

(defn remove-del-elements [diff]
  (filter #(not= :del (first %)) diff))

(defn get-hiccup-draft [draft-id] 
  (apply list (:content (drafts/retrieve-draft draft-id))))

(defn extract-chars-from-diff [diff char-count]
  #_{:new-diff diff
   :extracted-chars extracted-diff 
   }
  )

(defn split-element-at-position [element char-position]
  (let [element-type (first element)
        element-content (nth element 2)]
   [[element-type {} (subs element-content 0 char-position)] 
    [element-type {} (subs element-content char-position)]]))

(defn wrap-with-p-tag [elements]
  (apply merge [:p {}] elements))

(defn format-diff [paragraph-size char-count-for-diff diffs]
 (let [cumulative-sum (reductions + char-count-for-diff)
       index (count (filter #(< % paragraph-size) cumulative-sum))
       complete-elements (take index diffs)
       new-char-count (drop index char-count-for-diff)
       new-diffs (drop index diffs)
       final-element (if (> (nth cumulative-sum index) paragraph-size)
                       (first (split-element-at-position (nth diffs index) (- paragraph-size (nth cumulative-sum (dec index))))) 
                       (nth diffs index))
       elements-to-format (concat complete-elements [final-element])
       remaining-diff-char-count (if (> (nth cumulative-sum index) paragraph-size)
                                   [(- (first new-char-count) (- paragraph-size (nth cumulative-sum (dec index)))) (rest new-char-count)]
                                   (rest new-char-count))
       remaining-diffs (if (> (nth cumulative-sum index) paragraph-size)
                                   (second (split-element-at-position (nth diffs index) (- paragraph-size (nth cumulative-sum (dec index))))) 
                                   (rest diffs))]
   (prn remaining-diff-char-count)
   (prn remaining-diffs)
   {:formatted-paragraph (wrap-with-p-tag elements-to-format)
    :remaining-diff-char-count remaining-diff-char-count 
    :remaining-diffs remaining-diffs}))

(defn get-chars-from-diff [diff char-counts]
  
  )



(defn get-character-count-for-element [element]
  (if (> (count element) 2) (count (nth element 2)) 0))

(defn get-character-counts-for-draft [hiccup-draft]
  (map get-character-count-for-element hiccup-draft))

(defn add-formatting [diff draft]
 (let [char-counts-for-draft (get-character-counts-for-draft draft)
       _ (prn char-counts-for-draft)
       diff-without-deletes (remove-del-elements (dmp/as-hiccup diff))
       _ (prn diff-without-deletes)
       char-count-for-diff (get-character-counts-for-draft diff-without-deletes)
       _ (prn char-count-for-diff)
        formatted-first-paragraph (format-diff (first char-counts-for-draft) char-count-for-diff diff-without-deletes)
       formatted-second-paragraph (format-diff (second char-counts-for-draft) (:remaining-diff-char-count formatted-first-paragraph) (:remaining-diffs formatted-first-paragraph))]
   (prn formatted-first-paragraph)
   (prn formatted-second-paragraph)))


(defn get-all-views []
  (let [draft-1 (get-hiccup-draft 31572)
        draft-2 (get-hiccup-draft 31573)
        diffs (dmp/cleanup! (dmp/diff (get-text draft-1) (get-text draft-2)))]
    (add-formatting diffs draft-2)
    {:first-draft draft-1
     :diff (dmp/as-hiccup diffs) 
     :second-draft draft-2}))



(defn get-diff []
  (let [draft-1 (get-hiccup-draft 31572)
        draft-2 (get-hiccup-draft 31573)
        diffs (dmp/cleanup! (dmp/diff (get-text draft-1) (get-text draft-2)))
        inserts (remove nil? (map get-insert diffs))
        deletes (remove nil? (map get-delete diffs))]
    {:inserts inserts 
     :deletes deletes  
     :second-draft draft-2}))

