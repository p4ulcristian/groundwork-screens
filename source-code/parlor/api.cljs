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
                                       :on-answer-failed   [:app/answer-failed]   ; or nil
                                       :on-move            nil}])                 ; or an event
     (parlor/mount-root! \"app\" root)

     (rf/dispatch [:parlor/watch :order-panel [[:orders/one {:order/id id}]]])  ; adds
     (rf/dispatch [:parlor/own :order-panel [[:orders/one {:order/id id}]]])    ; exactly these
     (rf/dispatch [:parlor/release :order-panel])
     @(rf/subscribe [:parlor/answer [:orders/one {:order/id id}]])
     ;=> {:status :loading}, {:status :ready :data …}, {:status :error :error …},
     ;   {:status :refresh-error :data old :error …}
     (rf/dispatch [:parlor/ask-again [:orders/one {:order/id id}]])
     (rf/dispatch [:parlor/once-answered [:orders/one {:order/id id}] [:order-form/fill]])
     (rf/dispatch [:parlor/send [:orders/move {:order/id id :order/stage :curing}] [:orders/moved]])
     @(rf/subscribe [:parlor/can-i? :orders/edit [:shop shop-id]])
     @(rf/subscribe [:parlor/page])   ;=> {:name :orders/page :page … :path-params … :query …} or nil
     (rf/dispatch [:parlor/go-to :orders/order {:order-id id}])

   Nothing asks before :parlor/start: :parlor/watch, :parlor/own,
   :parlor/ask-again, :parlor/once-answered and :parlor/send throw
   (:parlor/asked-before-start), naming the messages. Every start setting
   is required (start-settings-shape); a missing or unknown one refuses,
   :parlor/bad-settings, with every problem at once."
  (:require
   [parlor.answers]
   [parlor.can-i]
   [parlor.pages]
   [parlor.screen :as screen]
   [parlor.settings :as settings]
   [parlor.start]))

(def start-settings-shape
  ":parlor/start's settings, every key required: :store :send-write!
   :retry-writes :pages :questions-of-pages :actions-question :on-answer-failed :on-move
   (see parlor.settings)."
  settings/start-settings-shape)

(def actions-answer-shape
  "The answer :parlor/can-i? reads, for the :answer-shape of the server's
   message that answers the start's :actions-question:
   [{:scope-kind :shop :scope-id #uuid \"…\" :actions [:orders/see …]} …];
   nil kind and id: in every scope."
  settings/actions-answer-shape)

(defn mount-root!
  "Draws [root] into the page's element with this id, with Reagent; call
   it again after a save in development: it reuses the element's React
   root and forgets re-frame's cached subscriptions."
  [element-id root]
  (screen/mount-root! element-id root))
