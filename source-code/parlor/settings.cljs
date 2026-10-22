(ns parlor.settings
  "parlor's start settings, as a malli shape, checked before anything
   starts. Nothing has a default: every key is written out, nil where
   nothing applies, and a missing or unknown one refuses, named, with every
   other problem at once (:parlor/bad-settings). They are values the app
   hands in, not data crossing an edge, so malli checks them itself."
  (:require
   [malli.core :as malli]
   [malli.error :as malli-error]))

(def question-shape
  "A question, as doorbell keeps it: [name params]."
  [:tuple :qualified-keyword :any])

(def event-shape
  "A re-frame event: a vector whose first value is the event's keyword."
  [:and vector? [:fn {:error/message "should be a re-frame event, [:its/name …]"} #(keyword? (first %))]])

(def start-settings-shape
  ":parlor/start's settings, every key required.
   :store: doorbell's question store (doorbell.api/make-question-store);
   parlor keeps its answers from it.
   :send-message!: (fn [message]) -> a promise of the answer keyed by
   name, with :mailroom/notes beside it: the function the store asks
   through. Writes are sent through it.
   :pages: the pages the server put in the page, for mailroom's
   start-following-address!.
   :questions-of-pages: every page's declaration of the questions it
   needs while it is open, by page name, one for each of :pages and no
   other: (fn [page]) -> questions, page {:name :page :path-params :query};
   (fn [_] []) for a page that needs none.
   :actions-question: the question whose answer says what the person may
   do, for :parlor/can-i?; nil when the app asks none.
   :on-answer-failed: an event, given the question and the failed answer,
   for every answer that failed; nil for none.
   :on-move: an event, given {:from page :to page}, after every move;
   nil for none."
  [:map {:closed true}
   [:store [:fn {:error/message "should be doorbell's question store, made by doorbell.api/make-question-store"} map?]]
   [:send-message! fn?]
   [:pages map?]
   [:questions-of-pages [:map-of :qualified-keyword fn?]]
   [:actions-question [:maybe question-shape]]
   [:on-answer-failed [:maybe event-shape]]
   [:on-move [:maybe event-shape]]])

(def actions-answer-shape
  "The answer :parlor/can-i? reads, the server's: every scope the person
   is in with the actions they may do there; a scope of nil kind and id
   gives its actions in every scope."
  [:vector [:map {:closed true}
            [:scope-kind [:maybe :keyword]]
            [:scope-id [:maybe :uuid]]
            [:actions [:vector :keyword]]]])

(defn- find-undeclared-pages
  "Every page :pages has and :questions-of-pages doesn't, and the other
   way: {:questions-of-pages [\"…\"]}, or nil."
  [{:keys [pages questions-of-pages]}]
  (let [missing (remove (set (keys questions-of-pages)) (keys pages))
        extra   (remove (set (keys pages)) (keys questions-of-pages))]
    (when (or (seq missing) (seq extra))
      {:questions-of-pages (vec (concat (map #(str "no questions declared for the page " %) missing)
                                        (map #(str "no page " % " in :pages") extra)))})))

(defn check-start-settings
  "Returns the settings; throws {:type :parlor/bad-settings :errors …}
   with every problem at once when they don't fit, or when a page in
   :pages declares no questions or questions are declared for a page
   :pages doesn't have."
  [settings]
  (let [errors (if-let [explanation (malli/explain start-settings-shape settings)]
                 (malli-error/humanize explanation)
                 (find-undeclared-pages settings))]
    (when errors
      (throw (ex-info (str "The settings have problems: " (pr-str errors))
                      {:type :parlor/bad-settings :errors errors}))))
  settings)
