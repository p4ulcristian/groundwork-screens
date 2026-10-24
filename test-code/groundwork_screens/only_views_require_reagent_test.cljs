(ns groundwork-screens.only-views-require-reagent-test
  "Everything of groundwork-screens that is Reagent's is in
   groundwork-screens.views: no other namespace of it requires Reagent.
   Read from the source files, on node, from the repository's root."
  (:require
   [cljs.test :refer [deftest is]]
   [clojure.string :as str]))

(def ^:private fs (js/require "fs"))

(def ^:private folder "source-code/groundwork_screens")

(defn- find-required-namespaces
  "The namespaces a source file's ns form requires, as text."
  [text]
  (let [ns-form (subs text 0 (or (str/index-of text "\n\n(") (count text)))]
    (set (map second (re-seq #"\[([a-z][a-z0-9.\-]*)" (subs ns-form (or (str/index-of ns-form "(:require") (count ns-form))))))))

(deftest only-the-views-require-reagent
  (let [files (filter #(str/ends-with? % ".cljs") (js->clj (.readdirSync fs folder)))
        uses  (into {} (for [file files]
                         [file (set (filter #(str/starts-with? % "reagent") (find-required-namespaces (str (.readFileSync fs (str folder "/" file) "utf8")))))]))]
    (is (seq (get uses "views.cljs")) "the views do")
    (is (= {} (into {} (remove (comp empty? val)) (dissoc uses "views.cljs")))
        "answers, pages, can-i, start, texts, settings, routes and the api require none")))
