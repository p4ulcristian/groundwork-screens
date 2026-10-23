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
   :send-write!: (fn [message {:keys [once-key keep-alive?]}]) -> a
   promise of the answer keyed by name, with :bridge/notes beside it:
   what :parlor/send sends a write through, with the send's options as
   the send wrote them out (send-options-shape), the same on each retry
   of that send (groundwork: bridge's send-message! with the key in the
   idempotency-key header and :keep-alive? as it is). bridge's send-message!: a
   promise that doesn't fail for want of an answer; the network, the
   server down, a page that isn't an answer are its :bridge/no-answer.
   :retry-writes: {:times n :wait-ms ms}: a send answered :bridge/no-answer
   is sent again, with the same key, up to :times more, :wait-ms apart, so
   the server saves it once; {:times 0 …} for never. Any other refusal is
   the server's answer, and isn't sent again.
   :pages: the pages the server put in the page, for bridge's
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
   :on-signed-out: an event, given the page the person was on, {:page
   name :params path-params :query query} ({:page nil :params {} :query
   {}} on an address no page has), so that after signing in they land
   there: dispatched once per start, at the first answer (a question's or a
   write's) refused with :signed-out-reason, however many come; nil for
   none.
   :signed-out-reason: the reason a refusal says nobody is signed in by,
   doorman's :doorman/signed-out in groundwork; nil when :on-signed-out
   is nil.
   :on-move: an event, given {:from page :to page}, after every move;
   nil for none.
   :query-shapes-of-pages: every page's query shape, by page name, one for
   each of :pages and no other: a malli map the query's text values are
   read by (bridge's), or nil to keep them text.
   :languages: nil without languages, or {:dictionary settings
   :language-of-page \"en\"}: dictionary's settings (the server's, from
   the page) and the language the page came in; an address's prefix says
   its language, and a move keeps it.
   :load-module!: (fn [module-name]) -> a promise, settled once a module
   loaded on demand has its stylesheets and code in (browser-build's
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
   [:on-signed-out [:maybe event-shape]]
   [:signed-out-reason [:maybe :qualified-keyword]]
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

(defn- find-signed-out-problems
  "An :on-signed-out needs the reason it hears, and none needs none."
  [{:keys [on-signed-out signed-out-reason]}]
  (when (not= (nil? on-signed-out) (nil? signed-out-reason))
    {:signed-out-reason [(if on-signed-out
                           "the :on-signed-out event needs the reason it is dispatched for"
                           "no :on-signed-out: nil")]}))

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
   :pages doesn't have, when only one of :on-signed-out and
   :signed-out-reason is nil, or when a page names a module and
   :load-module! or :placeholder is nil."
  [settings]
  (let [errors (if-let [explanation (malli/explain start-settings-shape settings)]
                 (malli-error/humanize explanation)
                 (merge (find-undeclared-pages settings) (find-actions-problems settings)
                        (find-signed-out-problems settings) (find-module-problems settings)))]
    (when errors
      (throw (ex-info (str "The settings have problems: " (pr-str errors))
                      {:type :parlor/bad-settings :errors errors}))))
  settings)

(def hold-shape
  "[:parlor/hold hold]'s hold, every key written out:
   :ask-with: how leaving is asked, :browser-confirm (the browser's
   confirm, the text made when it asks), or an event, the app's own
   dialog, dispatched with {:move :text :args} while the move is held
   back ([:parlor/leave-anyway move] finishes it).
   :text and :args: a text's key and its args, {} for none, as tr takes
   them: {:text :batches/unsaved :args {:count 3}}."
  [:map {:closed true}
   [:ask-with [:or [:= :browser-confirm] event-shape]]
   [:text :keyword]
   [:args [:map-of :keyword :any]]])

(defn check-hold
  "Returns the hold; throws :parlor/bad-hold with every problem at once (a
   ready-made text is one: the key and its args are held, so the text
   follows the language)."
  [hold]
  (when-let [explanation (malli/explain hold-shape hold)]
    (let [errors (malli-error/humanize explanation)]
      (throw (ex-info (str "[:parlor/hold hold] takes how to ask, a text's key and its args, {:ask-with :browser-confirm :text key :args {}}: " (pr-str errors))
                      {:type :parlor/bad-hold :errors errors}))))
  hold)

(def send-options-shape
  "[:parlor/send message on-answer options]'s options, every key written
   out:
   :once-key: the write's one-time key, text (make-once-key), made by the
   caller: a form makes it when its draft starts, keeps it beside the
   draft and passes it on every Save of that draft, so the server answers
   a second Save with the first one's answer; a new draft, a new key.
   :keep-alive?: true, the request finishes even when the page closes
   (fetch's keepalive; its body, with the other keepalive requests in
   flight, may not pass 64 KB); false, a plain request."
  [:map {:closed true}
   [:once-key [:string {:min 1}]]
   [:keep-alive? :boolean]])

(defn check-send-options
  "Returns a send's options; throws :parlor/bad-send with every problem at
   once (none written out among them: a new key for each send would be a
   hidden default)."
  [options]
  (when-let [explanation (malli/explain send-options-shape options)]
    (let [errors (malli-error/humanize explanation)]
      (throw (ex-info (str "[:parlor/send message on-answer options] takes its options written out, {:once-key (parlor/make-once-key) :keep-alive? false}: " (pr-str errors))
                      {:type :parlor/bad-send :errors errors}))))
  options)
