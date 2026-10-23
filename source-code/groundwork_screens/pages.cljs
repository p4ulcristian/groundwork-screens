(ns groundwork-screens.pages
  "The page the address bar leads to, as re-frame data, from bridge's
   routing. On every move the db's :groundwork-screens :page becomes bridge's page,
   {:name :page :path-params :query :query-errors :fragment :language}, or
   nil for an address no page has (its query read by the start's
   :query-shapes-of-pages, its language by groundwork-languages's rules, the
   start's :languages); the owner :groundwork-screens/page owns exactly the questions
   the new page declares (its :questions-of-pages; what the next page
   shares is kept, the rest let go, so a change of only the query keeps
   what it still asks); then the app's :on-move event gets {:from :to}.
   Moving is an event, [:groundwork-screens/go-to name path-params query way], way
   :push (a history entry, which Back returns from) or :replace (in place
   of the entry shown), written out; a move keeps the language.

   Scrolling: once the page is drawn (Reagent's after-render), as bridge
   tells: a new page from the top or at its #fragment, a change of only the
   query where it is, Back and Forward where that entry was left.

   Unsaved changes: [:groundwork-screens/hold {:ask-with … :text :batches/unsaved
   :args {:count 3}}] while a form is dirty, how to ask and a text's key
   and its args, [:groundwork-screens/let-go] after saving. While held, moving to
   another page (a link, :groundwork-screens/go-to, Back and Forward) asks first:
   - :ask-with :browser-confirm: the browser's confirm, the text made when
     it asks, in the language the texts are shown in then (the key's name
     while no catalog is in): yes lets go and moves, no stays;
   - :ask-with an event, the app's own dialog: the move is held back,
     groundwork-screens stays where it is and dispatches the event with {:move :text
     :args}; the dialog's Leave dispatches [:groundwork-screens/leave-anyway move],
     which lets go and finishes the move as it would have gone (Back and
     Forward go back for real, adding no history entry); Stay does
     nothing.
   Closing or reloading the tab asks the browser's own question.

   Loading on demand: a page whose :module (the server's pages) isn't
   loaded yet has it loaded on the way in, by the start's :load-module!
   (browser-build's, its stylesheets before its code); meanwhile
   [screens/show-page draw-page] draws the start's :placeholder instead of
   the page, and the scroll waits for the page."
  (:require
   [groundwork-languages.api :as languages]
   [groundwork-bridge.api :as bridge]
   [groundwork-screens.answers :as answers]
   [groundwork-screens.texts :as texts]
   [groundwork-screens.settings :as settings]
   [re-frame.core :as rf]
   [re-frame.db :as rf-db]
   [reagent.core :as reagent]))

;; -----------------------------------------------------------------------------
;; Unsaved changes

(defonce ^:private holding
  ;; how to ask before leaving and what, {:ask-with :text :args}, or nil
  (atom nil))

(defn- warn-before-closing [^js event]
  (.preventDefault event)
  (set! (.-returnValue event) ""))

(defn- has-window? [] (exists? js/window))

(defn ask-person
  "Whether the person says yes to the message: the browser's confirm."
  [message]
  (js/confirm message))

(defn- let-go! []
  (reset! holding nil)
  (when (has-window?) (js/window.removeEventListener "beforeunload" warn-before-closing)))

(defn- make-text-of-hold
  "The held text, made now in the language the texts are shown in: the
   browser's confirm is no view, so tr can't make it."
  [{:keys [text args]}]
  (texts/find-text-or-name (:groundwork-screens @rf-db/app-db) text args))

(defn may-leave?
  "bridge's :may-leave?, asked with the move: yes when nothing is held.
   Held with :browser-confirm, the person is asked the held text, in the
   language shown now, and yes lets go. Held with an event, no: the move
   is held back and the event dispatched with {:move :text :args}, for the
   app's dialog, whose Leave is [:groundwork-screens/leave-anyway move]."
  [move]
  (if-let [{:keys [ask-with text args] :as held} @holding]
    (if (= :browser-confirm ask-with)
      (boolean (when (ask-person (make-text-of-hold held)) (let-go!) true))
      (do (rf/dispatch (conj ask-with {:move move :text text :args args}))
          false))
    true))

(rf/reg-fx :groundwork-screens/-hold
           (fn [text]
             (when (and (nil? @holding) (has-window?))
               (js/window.addEventListener "beforeunload" warn-before-closing))
             (reset! holding text)))

(rf/reg-fx :groundwork-screens/-let-go (fn [_] (let-go!)))

(rf/reg-fx :groundwork-screens/-leave-anyway
           (fn [move]
             (let-go!)
             (bridge/finish-move! move)))

(rf/reg-event-fx :groundwork-screens/hold
                 (fn [_ [_ text]]
                   {:groundwork-screens/-hold (settings/check-hold text)}))

(rf/reg-event-fx :groundwork-screens/let-go
                 (fn [_ _]
                   {:groundwork-screens/-let-go nil}))

(rf/reg-event-fx :groundwork-screens/leave-anyway
                 (fn [_ [_ move]]
                   {:groundwork-screens/-leave-anyway move}))

;; -----------------------------------------------------------------------------
;; Following the address bar

(defn make-languages-for-bridge
  "bridge's :languages from the start's: groundwork-languages's functions over its
   settings, and the language the page came in; nil for nil."
  [languages]
  (when-let [{settings :settings :keys [language-of-page]} languages]
    {:split-language-from-path #(languages/split-language-from-path settings %)
     :make-path-in-language    #(languages/make-path-in-language settings %1 %2)
     :language-of-page         language-of-page}))

(rf/reg-fx :groundwork-screens/-follow-address
           (fn [{:keys [pages query-shapes-of-pages languages]}]
             (bridge/start-following-address! pages {:on-page               #(rf/dispatch [:groundwork-screens/-arrived %1 %2])
                                                       :query-shapes-of-pages query-shapes-of-pages
                                                       :may-leave?            may-leave?
                                                       :languages             (make-languages-for-bridge languages)})))

(rf/reg-fx :groundwork-screens/-go-to
           (fn [[page-name path-params query way]]
             (bridge/go-to-page! page-name path-params query way)))

(defn scroll-after-drawing!
  "Scrolls as bridge told, once Reagent has drawn the page."
  [scroll]
  (reagent/after-render #(bridge/scroll-as-told! scroll)))

(rf/reg-fx :groundwork-screens/-scroll (fn [scroll] (scroll-after-drawing! scroll)))

(rf/reg-fx :groundwork-screens/-load-module
           (fn [{:keys [load-module! module-name]}]
             (-> (load-module! module-name)
                 (.then #(rf/dispatch [:groundwork-screens/-module-loaded module-name])
                        #(rf/dispatch [:groundwork-screens/-module-failed module-name])))))

(defn find-module-of-page
  "The module a page names (the server's :module), or nil."
  [page]
  (get-in page [:page :module]))

(defn- is-module-loading? [db module-name]
  (and module-name (not= :loaded (get-in db [:groundwork-screens :modules module-name]))))

(rf/reg-event-fx :groundwork-screens/-arrived
                 (fn [{:keys [db]} [_ page scroll]]
                   (let [{:keys [questions-of-pages on-move load-module!]} (get-in db [:groundwork-screens :started])
                         from         (get-in db [:groundwork-screens :page])
                         module-name  (find-module-of-page page)
                         status       (get-in db [:groundwork-screens :modules module-name])
                         load?        (and module-name (not (#{:loading :loaded} status)))
                         {:keys [fx] :as owned} (answers/own-in-db (cond-> (assoc-in db [:groundwork-screens :page] page)
                                                                     load? (assoc-in [:groundwork-screens :modules module-name] :loading)
                                                                     true  (assoc-in [:groundwork-screens :waiting-scroll]
                                                                                     (when (is-module-loading? db module-name) scroll)))
                                                                   :groundwork-screens/page
                                                                   (if page (vec ((get questions-of-pages (:name page)) page)) []))
                         [screens fetch-fx] (texts/fetch-what-is-missing (:groundwork-screens (:db owned)))
                         owned             (assoc-in owned [:db :groundwork-screens] (texts/show-texts-in-wanted-language screens))]
                     (assoc owned :fx (cond-> (into fx fetch-fx)
                                        load?                                     (conj [:groundwork-screens/-load-module {:load-module! load-module!
                                                                                                               :module-name  module-name}])
                                        (not (is-module-loading? db module-name)) (conj [:groundwork-screens/-scroll scroll])
                                        on-move                                   (conj [:dispatch (conj on-move {:from from :to page})]))))))

(defn- scroll-if-the-page-waits
  "The scroll a page whose module just loaded waited for."
  [db module-name]
  (let [scroll (get-in db [:groundwork-screens :waiting-scroll])]
    (when (and scroll (= module-name (find-module-of-page (get-in db [:groundwork-screens :page]))))
      [[:groundwork-screens/-scroll scroll]])))

(rf/reg-event-fx :groundwork-screens/-module-loaded
                 (fn [{:keys [db]} [_ module-name]]
                   {:db (-> db
                            (assoc-in [:groundwork-screens :modules module-name] :loaded)
                            (assoc-in [:groundwork-screens :waiting-scroll] nil))
                    :fx (scroll-if-the-page-waits db module-name)}))

(rf/reg-event-db :groundwork-screens/-module-failed
                 (fn [db [_ module-name]]
                   (assoc-in db [:groundwork-screens :modules module-name] :failed)))

(rf/reg-event-fx :groundwork-screens/go-to
                 (fn [_ [_ page-name path-params query way]]
                   {:groundwork-screens/-go-to [page-name path-params query way]}))

(rf/reg-sub :groundwork-screens/page (fn [db _] (get-in db [:groundwork-screens :page])))

(rf/reg-sub :groundwork-screens/-module-status
            (fn [db [_ module-name]]
              (get-in db [:groundwork-screens :modules module-name])))

(rf/reg-sub :groundwork-screens/-placeholder (fn [db _] (get-in db [:groundwork-screens :started :placeholder])))

(defn show-page
  "A component: [draw-page page], the page shown (nil for an address no
   page has); while the page's module loads, [placeholder page status]
   instead, the start's :placeholder, status :loading, or :failed when it
   couldn't load."
  [draw-page]
  (let [page        @(rf/subscribe [:groundwork-screens/page])
        module-name (find-module-of-page page)
        status      @(rf/subscribe [:groundwork-screens/-module-status module-name])
        placeholder @(rf/subscribe [:groundwork-screens/-placeholder])]
    (if (and module-name (not= :loaded status))
      [placeholder page status]
      [draw-page page])))
