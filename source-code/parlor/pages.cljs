(ns parlor.pages
  "The page the address bar leads to, as re-frame data, from mailroom's
   routing. On every move the db's :parlor :page becomes mailroom's page,
   {:name :page :path-params :query :query-errors :fragment :language}, or
   nil for an address no page has (its query read by the start's
   :query-shapes-of-pages, its language by dictionary's rules, the
   start's :languages); the owner :parlor/page owns exactly the questions
   the new page declares (its :questions-of-pages; what the next page
   shares is kept, the rest let go, so a change of only the query keeps
   what it still asks); then the app's :on-move event gets {:from :to}.
   Moving is an event, [:parlor/go-to name path-params query]; a move
   keeps the language.

   Scrolling: once the page is drawn (Reagent's after-render), as mailroom
   tells: a new page from the top or at its #fragment, a change of only the
   query where it is, Back and Forward where that entry was left.

   Unsaved changes: [:parlor/hold \"message\"] while a form is dirty,
   [:parlor/let-go] after saving. While held, moving to another page (a
   link, :parlor/go-to, Back and Forward) asks the person the message
   first (the browser's confirm): yes lets go and moves, no stays; closing
   or reloading the tab asks the browser's own question.

   Loading on demand: a page whose :module (the server's pages) isn't
   loaded yet has it loaded on the way in, by the start's :load-module!
   (showroom's, its stylesheets before its code); meanwhile
   [parlor/show-page draw-page] draws the start's :placeholder instead of
   the page, and the scroll waits for the page."
  (:require
   [dictionary.api :as dictionary]
   [mailroom.api :as mailroom]
   [parlor.answers :as answers]
   [parlor.texts :as texts]
   [re-frame.core :as rf]
   [reagent.core :as reagent]))

;; -----------------------------------------------------------------------------
;; Unsaved changes

(defonce ^:private holding
  ;; the message to ask before leaving, or nil
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

(defn may-leave?
  "mailroom's :may-leave?: yes when nothing is held; else the person is
   asked the message, and yes lets go."
  []
  (if-let [message @holding]
    (boolean (when (ask-person message) (let-go!) true))
    true))

(rf/reg-fx :parlor/-hold
           (fn [message]
             (when (and (nil? @holding) (has-window?))
               (js/window.addEventListener "beforeunload" warn-before-closing))
             (reset! holding message)))

(rf/reg-fx :parlor/-let-go (fn [_] (let-go!)))

(rf/reg-event-fx :parlor/hold
                 (fn [_ [_ message]]
                   {:parlor/-hold message}))

(rf/reg-event-fx :parlor/let-go
                 (fn [_ _]
                   {:parlor/-let-go nil}))

;; -----------------------------------------------------------------------------
;; Following the address bar

(defn make-languages-for-mailroom
  "mailroom's :languages from the start's: dictionary's functions over its
   settings, and the language the page came in; nil for nil."
  [languages]
  (when-let [{settings :dictionary :keys [language-of-page]} languages]
    {:split-language-from-path #(dictionary/split-language-from-path settings %)
     :make-path-in-language    #(dictionary/make-path-in-language settings %1 %2)
     :language-of-page         language-of-page}))

(rf/reg-fx :parlor/-follow-address
           (fn [{:keys [pages query-shapes-of-pages languages]}]
             (mailroom/start-following-address! pages {:on-page               #(rf/dispatch [:parlor/-arrived %1 %2])
                                                       :query-shapes-of-pages query-shapes-of-pages
                                                       :may-leave?            may-leave?
                                                       :languages             (make-languages-for-mailroom languages)})))

(rf/reg-fx :parlor/-go-to
           (fn [[page-name path-params query]]
             (mailroom/go-to-page! page-name path-params query)))

(defn scroll-after-drawing!
  "Scrolls as mailroom told, once Reagent has drawn the page."
  [scroll]
  (reagent/after-render #(mailroom/scroll-as-told! scroll)))

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
