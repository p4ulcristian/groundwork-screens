(ns parlor.settings
  "parlor's start settings, as a malli shape, checked before anything
   starts. Nothing has a default: every key is written out, nil where
   nothing applies, and a missing or unknown one refuses, named, with every
   other problem at once (:parlor/bad-settings). They are values the app
   hands in, not data crossing an edge, so malli checks them itself."
  (:require
   [dictionary.api :as dictionary]
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
   :send-write!: (fn [message once-key]) -> a promise of the answer keyed
   by name, with :mailroom/notes beside it: what :parlor/send sends a
   write through, with its one-time key, a fresh random id per send, the
   same on each retry of that send (groundwork: the store's send function with
   the key in the idempotency-key header). A promise that fails means no
   answer: the network, the server down, a page that isn't an answer.
   :retry-writes: {:times n :wait-ms ms}: a send that got no answer is sent
   again, with the same key, up to :times more, :wait-ms apart, so the
   server saves it once; {:times 0 …} for never.
   :pages: the pages the server put in the page, for mailroom's
   start-following-address!.
   :questions-of-pages: every page's declaration of the questions it
   needs while it is open, by page name, one for each of :pages and no
   other: (fn [page]) -> questions, page {:name :page :path-params :query};
   (fn [_] []) for a page that needs none.
   :actions-question: the question whose answer says what the person may
   do, for :parlor/can-i?: the message of doorman's make-my-actions-handler,
   [:doorman/my-actions {}] in groundwork; nil when the app asks none.
   :actions-answer-shape: the shape that question's answer fits (doorman's
   my-actions-answer-shape), which \"can I?\" checks it by; nil when
   :actions-question is nil.
   :wire: the app's wire (wire.api's make-wire-from-settings), which the
   answer is checked by: in the browser an id is its text.
   :on-answer-failed: an event, given the question and the failed answer,
   for every answer that failed; nil for none.
   :on-move: an event, given {:from page :to page}, after every move;
   nil for none.
   :query-shapes-of-pages: every page's query shape, by page name, one for
   each of :pages and no other: a malli map the query's text values are
   read by (mailroom's), or nil to keep them text.
   :languages: nil without languages, or {:dictionary settings
   :language-of-page \"en\"}: dictionary's settings (the server's, from
   the page) and the language the page came in; an address's prefix says
   its language, and a move keeps it.
   :load-module!: (fn [module-name]) -> a promise, settled once a module
   loaded on demand has its stylesheets and code in (showroom's
   load-module!), for a page whose :module isn't loaded; nil when no page
   names one.
   :placeholder: a component, (fn [page status]), drawn by show-page in
   place of the page while its module loads (:loading) or after it failed
   (:failed); nil when no page names a module."
  [:map {:closed true}
   [:store [:fn {:error/message "should be doorbell's question store, made by doorbell.api/make-question-store"} map?]]
   [:send-write! fn?]
   [:retry-writes [:map {:closed true}
                   [:times [:int {:min 0}]]
                   [:wait-ms [:int {:min 0}]]]]
   [:pages map?]
   [:questions-of-pages [:map-of :qualified-keyword fn?]]
   [:actions-question [:maybe question-shape]]
   [:actions-answer-shape :any]
   [:wire [some? {:error/message "should be the app's wire, made by wire.api/make-wire-from-settings"}]]
   [:on-answer-failed [:maybe event-shape]]
   [:on-move [:maybe event-shape]]
   [:query-shapes-of-pages [:map-of :qualified-keyword :any]]
   [:languages [:maybe [:map {:closed true}
                        [:dictionary dictionary/settings-shape]
                        [:language-of-page :string]]]]
   [:load-module! [:maybe fn?]]
   [:placeholder [:maybe fn?]]])

(defn- find-undeclared-pages-in
  "Every page :pages has and the setting k doesn't, and the other way:
   {k [\"…\"]}, or nil."
  [{:keys [pages] :as settings} k what]
  (let [declared (get settings k)
        missing  (remove (set (keys declared)) (keys pages))
        extra    (remove (set (keys pages)) (keys declared))]
    (when (or (seq missing) (seq extra))
      {k (vec (concat (map #(str "no " what " declared for the page " %) missing)
                      (map #(str "no page " % " in :pages") extra)))})))

(defn- find-undeclared-pages [settings]
  (merge (find-undeclared-pages-in settings :questions-of-pages "questions")
         (find-undeclared-pages-in settings :query-shapes-of-pages "query shape")))

(defn- find-actions-problems
  "An :actions-question needs its answer's shape, and none needs none."
  [{:keys [actions-question actions-answer-shape]}]
  (when (not= (nil? actions-question) (nil? actions-answer-shape))
    {:actions-answer-shape [(if actions-question
                              "the :actions-question's answer needs its shape"
                              "no :actions-question: nil")]}))

(defn- find-module-problems
  "A page that names a module needs :load-module! and :placeholder."
  [{:keys [pages load-module! placeholder]}]
  (when (some :module (vals pages))
    (cond-> nil
      (nil? load-module!) (assoc :load-module! ["a page names a module: say how one loads"])
      (nil? placeholder)  (assoc :placeholder ["a page names a module: say what shows while it loads"]))))

(defn check-start-settings
  "Returns the settings; throws {:type :parlor/bad-settings :errors …}
   with every problem at once when they don't fit, when a page in :pages
   declares no questions or query shape or one is declared for a page
   :pages doesn't have, or when a page names a module and :load-module!
   or :placeholder is nil."
  [settings]
  (let [errors (if-let [explanation (malli/explain start-settings-shape settings)]
                 (malli-error/humanize explanation)
                 (merge (find-undeclared-pages settings) (find-actions-problems settings) (find-module-problems settings)))]
    (when errors
      (throw (ex-info (str "The settings have problems: " (pr-str errors))
                      {:type :parlor/bad-settings :errors errors}))))
  settings)
