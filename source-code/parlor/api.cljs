(ns parlor.api
  "The room where guests sit and are shown things: the browser's screens
   over doorbell's question store, in re-frame, drawn with Reagent. Its
   events and subscriptions are named :parlor/…, registered when this
   namespace loads; its functions are below.

     ;; once, at the start, with the store and the send function the app made
     (rf/dispatch-sync [:parlor/start {:store              store            ; doorbell's question store
                                       :send-write!        send-write!      ; (fn [message once-key]): a write, with its one-time key
                                       :retry-writes       {:times 2 :wait-ms 1000}   ; a write that got no answer, sent again
                                       :pages              pages            ; the server's, from the page
                                       :questions-of-pages {:orders/page (fn [{:keys [path-params]}] […])}  ; each page's
                                       :actions-question   [:doorman/my-actions {}]   ; doorman's, or nil
                                       :actions-answer-shape doorman/my-actions-answer-shape   ; its answer's, or nil
                                       :wire               wire                   ; the app's, which the answer is checked by
                                       :on-answer-failed   [:app/answer-failed]   ; or nil
                                       :on-signed-out      [:app/to-login]        ; gets {:page :params :query}, once; or nil
                                       :signed-out-reason  :doorman/signed-out    ; the reason it hears; nil with no :on-signed-out
                                       :on-move            nil                    ; or an event
                                       :query-shapes-of-pages {:orders/page [:map [:tab {:optional true} :keyword]]}  ; nil: text
                                       :languages          nil                    ; or {:dictionary settings :language-of-page \"en\"}
                                       :load-module!       nil                    ; or browser-build's, for pages that name a :module
                                       :placeholder        nil}])                 ; or (fn [page status]) while one loads
     (parlor/mount-root! \"app\" root)

     (rf/dispatch [:parlor/watch :order-panel [[:orders/one {:order/id id}]]])  ; adds
     (rf/dispatch [:parlor/own :order-panel [[:orders/one {:order/id id}]]])    ; exactly these
     (rf/dispatch [:parlor/release :order-panel])
     @(rf/subscribe [:parlor/answer [:orders/one {:order/id id}]])
     ;=> {:status :loading}, {:status :ready :data …}, {:status :error :error refusal},
     ;   {:status :refresh-error :data old :error refusal}
     ;   a refusal as bridge gives it: {:bridge/refused :order/already-shipped :bridge/data {…} :bridge/request-id \"…\"}
     (parlor/tr (:bridge/refused refusal) (:bridge/data refusal))   ; its text: the reason is the key, its data the args
     (rf/dispatch [:parlor/ask-again [:orders/one {:order/id id}]])
     (rf/dispatch [:parlor/once-answered [:orders/one {:order/id id}] [:order-form/fill]])
     (rf/dispatch [:parlor/send [:orders/move {:order/id id :order/stage :curing}] [:orders/moved]])
     @(rf/subscribe [:parlor/can-i? :orders/edit [:shop shop-id]])
     (parlor/tr :orders.cart/add {:count 3})                      ; in the page's language, in a screen or an event
     @(rf/subscribe [:parlor/text :orders.cart/add {:count 3}])   ; the same, as a subscription; nil until its catalog is in
     @(rf/subscribe [:parlor/texts-ready?])
     @(rf/subscribe [:parlor/page])   ;=> {:name :orders/page :page … :path-params … :query … :language …} or nil
     (rf/dispatch [:parlor/go-to :orders/order {:order-id id} {}])   ; a query, {} for none
     (rf/dispatch [:parlor/hold \"Leave without saving?\"])         ; asked before leaving the page
     (rf/dispatch [:parlor/let-go])
     [parlor/show-page draw-page]     ; the page, or the placeholder while its module loads

   Nothing asks before :parlor/start: :parlor/watch, :parlor/own,
   :parlor/ask-again, :parlor/once-answered and :parlor/send throw
   (:parlor/asked-before-start), naming the messages. Every start setting
   is required (start-settings-shape); a missing or unknown one refuses,
   :parlor/bad-settings, with every problem at once."
  (:require
   [parlor.answers]
   [parlor.can-i]
   [parlor.pages :as pages]
   [parlor.screen :as screen]
   [parlor.settings :as settings]
   [parlor.start]
   [parlor.texts :as texts]
   [re-frame.core :as rf]
   [re-frame.db :as rf-db]
   [reagent.ratom :as ratom]))

(def start-settings-shape
  ":parlor/start's settings, every key required: :store :send-write!
   :retry-writes :pages :questions-of-pages :actions-question :actions-answer-shape
   :wire :on-answer-failed :on-signed-out :signed-out-reason :on-move
   :query-shapes-of-pages :languages :load-module! :placeholder (see
   parlor.settings)."
  settings/start-settings-shape)

(defn show-page
  "A component: [draw-page page], the page shown (nil for an address no
   page has), or, while the page's :module loads, the start's
   [placeholder page status], status :loading or :failed."
  [draw-page]
  [pages/show-page draw-page])

(defn mount-root!
  "Draws [root] into the page's element with this id, with Reagent, once
   the first catalogs are in (until then the element keeps what the page
   put in it); call it after :parlor/start (before it, it throws
   :parlor/mounted-before-start), and again after a save in development:
   it reuses the element's React root and forgets re-frame's cached
   subscriptions."
  [element-id root]
  (screen/mount-root! element-id root))

(defn tr
  "The key's message in the page's language, args filled in ({} for
   none): [:parlor/text key args]. In a screen it is that subscription,
   so a swapped catalog draws the screen again; elsewhere (an event, a
   helper) it reads re-frame's db as it is now. Inside the root
   mount-root! draws, never nil: the root waits for the first catalogs."
  [message-key args]
  (if (ratom/reactive?)
    @(rf/subscribe [:parlor/text message-key args])
    (texts/find-text (:parlor @rf-db/app-db) message-key args)))
