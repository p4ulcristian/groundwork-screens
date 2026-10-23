(ns parlor.pages
  "The page the address bar leads to, as re-frame data, from bridge's
   routing. On every move the db's :parlor :page becomes bridge's page,
   {:name :page :path-params :query :query-errors :fragment :language}, or
   nil for an address no page has (its query read by the start's
   :query-shapes-of-pages, its language by dictionary's rules, the
   start's :languages); the owner :parlor/page owns exactly the questions
   the new page declares (its :questions-of-pages; what the next page
   shares is kept, the rest let go, so a change of only the query keeps
   what it still asks); then the app's :on-move event gets {:from :to}.
   Moving is an event, [:parlor/go-to name path-params query]; a move
   keeps the language.

   Scrolling: once the page is drawn (Reagent's after-render), as bridge
   tells: a new page from the top or at its #fragment, a change of only the
   query where it is, Back and Forward where that entry was left.

   Unsaved changes: [:parlor/hold {:text :batches/unsaved :args {:count
   3}}] while a form is dirty, a text's key and its args, [:parlor/let-go]
   after saving. While held, moving to another page (a link,
   :parlor/go-to, Back and Forward) asks the person that text first (the
   browser's confirm), made when it asks, in the language the texts are
   shown in then (the key's name while no catalog is in): yes lets go and
   moves, no stays; closing or reloading the tab asks the browser's own
   question.

   Loading on demand: a page whose :module (the server's pages) isn't
   loaded yet has it loaded on the way in, by the start's :load-module!
   (browser-build's, its stylesheets before its code); meanwhile
   [parlor/show-page draw-page] draws the start's :placeholder instead of
   the page, and the scroll waits for the page."
  (:require
   [dictionary.api :as dictionary]
   [bridge.api :as bridge]
   [parlor.answers :as answers]
   [parlor.texts :as texts]
   [parlor.settings :as settings]
   [re-frame.core :as rf]
   [re-frame.db :as rf-db]
   [reagent.core :as reagent]))

;; -----------------------------------------------------------------------------
;; Unsaved changes

(defonce ^:private holding
  ;; the text to ask before leaving, {:text key :args args}, or nil
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
  (texts/find-text-or-name (:parlor @rf-db/app-db) text args))

(defn may-leave?
  "bridge's :may-leave?: yes when nothing is held; else the person is
   asked the held text, in the language shown now, and yes lets go."
  []
  (if-let [held @holding]
    (boolean (when (ask-person (make-text-of-hold held)) (let-go!) true))
    true))

(rf/reg-fx :parlor/-hold
           (fn [text]
             (when (and (nil? @holding) (has-window?))
               (js/window.addEventListener "beforeunload" warn-before-closing))
             (reset! holding text)))

(rf/reg-fx :parlor/-let-go (fn [_] (let-go!)))

(rf/reg-event-fx :parlor/hold
                 (fn [_ [_ text]]
                   {:parlor/-hold (settings/check-hold text)}))

(rf/reg-event-fx :parlor/let-go
                 (fn [_ _]
                   {:parlor/-let-go nil}))

;; -----------------------------------------------------------------------------
;; Following the address bar

(defn make-languages-for-bridge
  "bridge's :languages from the start's: dictionary's functions over its
   settings, and the language the page came in; nil for nil."
  [languages]
  (when-let [{settings :dictionary :keys [language-of-page]} languages]
    {:split-language-from-path #(dictionary/split-language-from-path settings %)
     :make-path-in-language    #(dictionary/make-path-in-language settings %1 %2)
     :language-of-page         language-of-page}))

(rf/reg-fx :parlor/-follow-address
           (fn [{:keys [pages query-shapes-of-pages languages]}]
             (bridge/start-following-address! pages {:on-page               #(rf/dispatch [:parlor/-arrived %1 %2])
                                                       :query-shapes-of-pages query-shapes-of-pages
                                                       :may-leave?            may-leave?
                                                       :languages             (make-languages-for-bridge languages)})))

(rf/reg-fx :parlor/-go-to
           (fn [[page-name path-params query]]
             (bridge/go-to-page! page-name path-params query)))

(defn scroll-after-drawing!
  "Scrolls as bridge told, once Reagent has drawn the page."
  [scroll]
  (reagent/after-render #(bridge/scroll-as-told! scroll)))

(rf/reg-fx :parlor/-scroll (fn [scroll] (scroll-after-drawing! scroll)))

(rf/reg-fx :parlor/-load-module
           (fn [{:keys [load-module! module-name]}]
             (-> (load-module! module-name)
                 (.then #(rf/dispatch [:parlor/-module-loaded module-name])
                        #(rf/dispatch [:parlor/-module-failed module-name])))))

(defn find-module-of-page
  "The module a page names (the server's :module), or nil."
  [page]
  (get-in page [:page :module]))

(defn- is-module-loading? [db module-name]
  (and module-name (not= :loaded (get-in db [:parlor :modules module-name]))))

(rf/reg-event-fx :parlor/-arrived
                 (fn [{:keys [db]} [_ page scroll]]
                   (let [{:keys [questions-of-pages on-move load-module!]} (get-in db [:parlor :started])
                         from         (get-in db [:parlor :page])
                         module-name  (find-module-of-page page)
                         status       (get-in db [:parlor :modules module-name])
                         load?        (and module-name (not (#{:loading :loaded} status)))
                         {:keys [fx] :as owned} (answers/own-in-db (cond-> (assoc-in db [:parlor :page] page)
                                                                     load? (assoc-in [:parlor :modules module-name] :loading)
                                                                     true  (assoc-in [:parlor :waiting-scroll]
                                                                                     (when (is-module-loading? db module-name) scroll)))
                                                                   :parlor/page
                                                                   (if page (vec ((get questions-of-pages (:name page)) page)) []))
                         [parlor fetch-fx] (texts/fetch-what-is-missing (:parlor (:db owned)))
                         owned             (assoc-in owned [:db :parlor] (texts/show-texts-in-wanted-language parlor))]
                     (assoc owned :fx (cond-> (into fx fetch-fx)
                                        load?                                     (conj [:parlor/-load-module {:load-module! load-module!
                                                                                                               :module-name  module-name}])
                                        (not (is-module-loading? db module-name)) (conj [:parlor/-scroll scroll])
                                        on-move                                   (conj [:dispatch (conj on-move {:from from :to page})]))))))

(defn- scroll-if-the-page-waits
  "The scroll a page whose module just loaded waited for."
  [db module-name]
  (let [scroll (get-in db [:parlor :waiting-scroll])]
    (when (and scroll (= module-name (find-module-of-page (get-in db [:parlor :page]))))
      [[:parlor/-scroll scroll]])))

(rf/reg-event-fx :parlor/-module-loaded
                 (fn [{:keys [db]} [_ module-name]]
                   {:db (-> db
                            (assoc-in [:parlor :modules module-name] :loaded)
                            (assoc-in [:parlor :waiting-scroll] nil))
                    :fx (scroll-if-the-page-waits db module-name)}))

(rf/reg-event-db :parlor/-module-failed
                 (fn [db [_ module-name]]
                   (assoc-in db [:parlor :modules module-name] :failed)))

(rf/reg-event-fx :parlor/go-to
                 (fn [_ [_ page-name path-params query]]
                   {:parlor/-go-to [page-name path-params query]}))

(rf/reg-sub :parlor/page (fn [db _] (get-in db [:parlor :page])))

(rf/reg-sub :parlor/-module-status
            (fn [db [_ module-name]]
              (get-in db [:parlor :modules module-name])))

(rf/reg-sub :parlor/-placeholder (fn [db _] (get-in db [:parlor :started :placeholder])))

(defn show-page
  "A component: [draw-page page], the page shown (nil for an address no
   page has); while the page's module loads, [placeholder page status]
   instead, the start's :placeholder, status :loading, or :failed when it
   couldn't load."
  [draw-page]
  (let [page        @(rf/subscribe [:parlor/page])
        module-name (find-module-of-page page)
        status      @(rf/subscribe [:parlor/-module-status module-name])
        placeholder @(rf/subscribe [:parlor/-placeholder])]
    (if (and module-name (not= :loaded status))
      [placeholder page status]
      [draw-page page])))
