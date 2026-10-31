(ns groundwork-screens.api
  "The room where guests sit and are shown things: the browser's screens
   over groundwork-live-updates's question store, in re-frame, drawn with Reagent. Its
   events and subscriptions are named :parlor/…, registered when this
   namespace loads; its functions are below.

     ;; once, at the start, with the store and the send function the app made
     (rf/dispatch-sync [:groundwork-screens/start {:store              store            ; groundwork-live-updates's question store
                                       :send-write!        send-write!      ; (fn [message {:once-key :keep-alive?}]): a write, with its send's options
                                       :retry-writes       {:times 2 :wait-ms 1000}   ; a write that got no answer, sent again
                                       :pages              pages            ; the server's, from the page
                                       :questions-of-pages {:orders/page (fn [{:keys [path-params]}] […])}  ; each page's
                                       :actions-question   [:groundwork-access/my-actions {}]   ; groundwork-access's, or nil
                                       :actions-answer-shape access/my-actions-answer-shape   ; its answer's, or nil
                                       :shaper               groundwork-shaped-json                   ; the app's, which the answer is checked by
                                       :on-answer-failed   [:app/answer-failed]   ; or nil
                                       :on-signed-out      [:app/to-login]        ; gets {:page :params :query}, once; or nil
                                       :signed-out-reason  :groundwork-access/signed-out    ; the reason it hears; nil with no :on-signed-out
                                       :on-move            nil                    ; or an event
                                       :query-shapes-of-pages {:orders/page [:map [:tab {:optional true} :keyword]]}  ; nil: text
                                       :languages          nil                    ; or {:settings settings :language-of-page \"en\"}
                                       :load-module!       nil                    ; or browser-build's, for pages that name a :module
                                       :placeholder        nil}])                 ; or (fn [page status]) while one loads
     (screens/mount-root! \"app\" root)

     (rf/dispatch [:groundwork-screens/watch :order-panel [[:orders/one {:order/id id}]]])  ; adds
     (rf/dispatch [:groundwork-screens/own :order-panel [[:orders/one {:order/id id}]]])    ; exactly these
     (rf/dispatch [:groundwork-screens/release :order-panel])
     @(rf/subscribe [:groundwork-screens/answer [:orders/one {:order/id id}]])
     ;=> {:status :loading}, {:status :ready :data …}, {:status :error :error refusal},
     ;   {:status :refresh-error :data old :error refusal}
     ;   a refusal, groundwork-steps's shape: {:groundwork-steps/refused :order/already-shipped :groundwork-steps/data {…} :groundwork-steps/request-id \"…\"}
     (screens/tr (:groundwork-steps/refused refusal) (:groundwork-steps/data refusal))   ; its text: the reason is the key, its data the args
     (screens/find-answer db [:orders/one {:order/id id}])   ; the same, in an event handler, from its db
     (rf/dispatch [:groundwork-screens/ask-again [:orders/one {:order/id id}]])
     (rf/dispatch [:groundwork-screens/once-answered [:orders/one {:order/id id}] [:order-form/fill]])
     (rf/dispatch [:groundwork-screens/once-settled [:partnerships/of-shop {:shop/id id}] [:wizard/continue]])   ; gets {:status :ready :data …} or {:status :error :error refusal}, once
     (rf/dispatch [:groundwork-screens/send [:orders/move {:order/id id :order/stage :curing}] [:orders/moved]
                   {:once-key (:once-key draft) :keep-alive? false}])   ; the key made with the draft: (screens/make-once-key)
     @(rf/subscribe [:groundwork-screens/can-i? :orders/edit [:shop shop-id]])
     (screens/tr :orders.cart/add {:count 3})   ; in the page's language, only in a view, which it subscribes
     ;; an event stores a text's key and its args; the view that shows it calls tr
     {:db (assoc-in db [:toasts id] {:text :order/already-shipped :args {:order/shipped-at t}})}
     (defn toast [{:keys [text args]}] [:div.toast (screens/tr text args)])
     @(rf/subscribe [:groundwork-screens/texts-ready?])
     @(rf/subscribe [:groundwork-screens/page])   ;=> {:name :orders/page :page … :path-params … :query … :language …} or nil
     (rf/dispatch [:groundwork-screens/go-to :orders/order {:order-id id} {} :push])   ; a query, {} for none; :push or :replace
     [:a {:href (screens/make-address-of-page :orders/order {:order-id id} {})} \"Order\"]   ; a link, in the page's language
     (rf/dispatch [:groundwork-screens/hold {:ask-with :browser-confirm :text :batches/unsaved :args {:count 3}}])  ; the browser's confirm, in the language shown then
     (rf/dispatch [:groundwork-screens/hold {:ask-with [:batches/ask-leave] :text :batches/unsaved :args {}}])  ; the app's dialog, given {:move :text :args}
     (rf/dispatch [:groundwork-screens/leave-anyway move])   ; the dialog's Leave: the held move goes as it would have
     (rf/dispatch [:groundwork-screens/let-go])
     [screens/show-page draw-page]     ; the page, or the placeholder while its module loads

   Nothing asks before :groundwork-screens/start: :groundwork-screens/watch, :groundwork-screens/own,
   :groundwork-screens/ask-again, :groundwork-screens/once-answered, :groundwork-screens/once-settled
   and :groundwork-screens/send throw
   (:groundwork-screens/asked-before-start), naming the messages. Every start setting
   is required (start-settings-shape); a missing or unknown one refuses,
   :groundwork-screens/bad-settings, with every problem at once.

   The address bar is followed here (groundwork-screens.routes): the pages
   are drawn here, so moving between them is groundwork-screens's too. What
   is Reagent's is in groundwork-screens.views alone; answers are told from
   refusals by groundwork-steps's shape; the store and the send function
   are the app's, made on its wire (groundwork's: groundwork-bridge's
   send-message!)."
  (:require
   [groundwork-screens.answers :as answers]
   [groundwork-screens.can-i]
   [groundwork-screens.pages]
   [groundwork-screens.routes :as routes]
   [groundwork-screens.settings :as settings]
   [groundwork-screens.start]
   [groundwork-screens.texts]
   [groundwork-screens.views :as views]))

(def start-settings-shape
  ":groundwork-screens/start's settings, every key required: :store :send-write!
   :retry-writes :pages :questions-of-pages :actions-question :actions-answer-shape
   :shaper :on-answer-failed :on-signed-out :signed-out-reason :on-move
   :query-shapes-of-pages :languages :load-module! :placeholder (see
   groundwork-screens.settings)."
  settings/start-settings-shape)

(defn make-once-key
  "A write's one-time key, for :groundwork-screens/send's :once-key: a random id,
   text. A form makes one when its draft starts, keeps it beside the
   draft, and passes it on every Save of that draft, so a Save pressed
   again after a network error is the same write; a new draft, a new
   key."
  []
  (answers/make-once-key))

(defn find-answer
  "A held answer, read inside an event handler from its db: what
   [:groundwork-screens/answer question] gives, {:status :loading}, {:status :ready
   :data …}, {:status :error :error refusal} or {:status :refresh-error
   :data old :error refusal}; {:status :loading} for a question nothing
   holds. It reads only: a question it should keep is held by an owner
   (:groundwork-screens/watch, :groundwork-screens/own, a page's :questions-of-pages)."
  [db question]
  (answers/find-answer db question))

(defn show-page
  "A component: [draw-page page], the page shown (nil for an address no
   page has), or, while the page's :module loads, the start's
   [placeholder page status], status :loading or :failed."
  [draw-page]
  [views/show-page draw-page])

(defn mount-root!
  "Draws [root] into the page's element with this id, with Reagent, once
   the first catalogs are in (until then the element keeps what the page
   put in it); call it after :groundwork-screens/start (before it, it throws
   :groundwork-screens/mounted-before-start), and again after a save in development:
   it reuses the element's React root and forgets re-frame's cached
   subscriptions."
  [element-id root]
  (views/mount-root! element-id root))

(defn tr
  "The key's message in the page's language, args filled in ({} for none;
   a key names its argument without its namespace, a - read as _), the
   one text call in the browser. It works only while a view draws: it
   subscribes, so the view draws again when the language or the catalog
   changes. Called anywhere else (an event handler, an effect, a
   callback) it throws, :groundwork-screens/tr-outside-a-view, naming the key: an
   event stores the key and its args, and the view that shows it calls
   tr. Inside the root mount-root! draws, never nil: the root waits for
   the first catalogs; a view drawn outside it gets nil until they are
   in."
  [message-key args]
  (views/tr message-key args))

(defn make-address-of-page
  "A page's address, for a link, with its path params and query ({} for
   none; a nil value leaves its key out), in the language of the page
   shown: (make-address-of-page :orders/page {:order-id 42} {:tab :notes})
   -> \"/de/orders/42?tab=notes\". A name or path params no page has
   throw, :groundwork-screens/no-page."
  [page-name path-params query]
  (routes/make-address-of-page page-name path-params query))
