# groundwork-screens

The room where guests sit and are shown things: the browser's screens over
[groundwork-live-updates](https://github.com/p4ulcristian/groundwork-live-updates)'s question store, in
[re-frame](https://github.com/day8/re-frame), drawn with
[Reagent](https://github.com/reagent-project/reagent). A screen says which
questions it shows; groundwork-screens keeps their answers in re-frame's db with one of
four statuses, has groundwork-live-updates watch them (asked again when a table they read
changes), sends writes and asks again what they touched, says what the
person may do, follows the address bar and puts the page it leads to in
the db (the routing is groundwork-screens's own: pages are drawn here, so
moving between them is here too).

```clojure
io.github.p4ulcristian/groundwork-screens {:git/tag "v0.8.7" :git/sha "23e3809"}
```

Needs re-frame 1.4.7, Reagent 2.0.1, reitit-frontend (the pages matched),
groundwork-live-updates (the question store, one layer down),
[groundwork-steps](https://github.com/p4ulcristian/groundwork-steps) (an answer told from a refusal),
groundwork-languages, groundwork-shaped-json and malli; react and react-dom come from the app's
`package.json`. Browser only, one public namespace, `groundwork-screens.api`,
which registers the events and subscriptions below when it loads.
groundwork-screens makes no store, no send function and no shaper, and
requires no wire: the app (groundwork) makes them on its own (groundwork-bridge's
`send-message!`) and hands them to the start
([why](decisions/2026-10-24-groundwork-screens-follows-the-address-bar-and-keeps-reagent-in-its-views.md)).
Everything Reagent's is in one namespace, `groundwork-screens.views` (`show-page`,
`mount-root!`, `tr`'s check that a view draws, the scroll once drawn); the
others (answers, pages, routes, can-i, start, texts, settings) require
re-frame and no Reagent, checked by a test.

## The api

| name | does |
|---|---|
| `[:groundwork-screens/start settings]` | once; every setting required (`start-settings-shape`), checked first; follows the address bar |
| `[:groundwork-screens/watch owner questions]` `[:groundwork-screens/own owner questions]` `[:groundwork-screens/release owner]` | an owner holds questions: adds them, exactly these (shared ones kept), none |
| `[:groundwork-screens/answer question]` (sub) | `{:status :loading}`, `:ready` with `:data`, `:error` with `:error`, `:refresh-error` with the old `:data` and the `:error`; an `:error` is a refusal, groundwork-steps's shape, `{:groundwork-steps/refused reason :groundwork-steps/data … :groundwork-steps/request-id …}`, its text `(screens/tr reason data)` |
| `find-answer` | `(screens/find-answer db question)`: the same as `[:groundwork-screens/answer question]`, read inside an event handler from its `db`; it reads only, and a question nothing holds is `{:status :loading}` ([why](decisions/2026-10-23-a-held-answer-is-read-in-an-event-handler-by-find-answer.md)) |
| `[:groundwork-screens/ask-again question]` | a held question asked again now |
| `[:groundwork-screens/once-answered question event]` | `event` gets the data once: now, or, while a fresher answer is on its way (groundwork-live-updates's `is-question-being-asked?`) or none is there, with the next good answer |
| `[:groundwork-screens/send [name params] event options]` `make-once-key` | a write, through `:send-write!` with its options written out, `{:once-key k :keep-alive? false}` (a missing one is refused, `:groundwork-screens/bad-send`): the key is the caller's, made with `(screens/make-once-key)` when a draft starts and passed on every Save of that draft, so a second Save is the same write; `:keep-alive? true` lets the write finish after the page closes ([why](decisions/2026-10-23-every-send-writes-out-its-options-its-once-key-among-them.md)); sent again with the same options while it is answered `:groundwork-steps/no-answer` (`:retry-writes`), never for another refusal; `event` gets `{:status :ready :data …}` or `{:status :error :error refusal}`; a good answer asks again what its notes name |
| `:on-signed-out` (a start setting) | the first answer (a question's or a write's) refused with `:signed-out-reason` dispatches it once per start, with the page to return to after signing in: `[:app/to-login {:page :orders/page :params {…} :query {…}}]` |
| `[:groundwork-screens/can-i? action scope]` (sub) | from the `:actions-question`'s answer, checked by `:actions-answer-shape` (groundwork-access's `my-actions-answer-shape`) through the app's `:shaper`; `scope` is `[kind id]` or a list of them |
| `[:groundwork-screens/page]` (sub) `[:groundwork-screens/go-to name path-params query way]` `make-address-of-page` | the page, `{:name :page :path-params :query :query-errors :fragment :language}` or nil; a move, keeping the language, `way` written out: `:push` adds a history entry, `:replace` takes the place of the one shown ([why](decisions/2026-10-23-a-move-says-whether-it-adds-a-history-entry.md)). Entering a page owns the questions it declares (`:questions-of-pages`), leaving releases them; a change of only the query keeps what it still asks, and the scroll. Each move scrolls once drawn: top, the `#fragment`, or where Back left it. A link's address: `(screens/make-address-of-page name path-params query)`, in the page's language |
| `tr` | `(screens/tr :orders.cart/add {:count 3})`: the one text call, a message in the page's language (groundwork-languages's catalogs, fetched by groundwork-screens; the fallback's, else the key's name); only while a view draws: it subscribes, so a new language or catalog draws the view again; anywhere else (an event, an effect) it throws, `:groundwork-screens/tr-outside-a-view`: an event stores the key and its args, the view calls `tr` ([why](decisions/2026-10-23-tr-is-called-only-in-views.md)); nil until the first catalogs are in, never inside the root `mount-root!` draws |
| `[:groundwork-screens/texts-ready?]` (sub) | whether the first catalogs are in (always, without `:languages`) |
| `[:groundwork-screens/hold {:ask-with … :text key :args args}]` `[:groundwork-screens/let-go]` `[:groundwork-screens/leave-anyway move]` | unsaved changes, held with how to ask and a text's key and its args (`{}` for none; a ready-made text is refused, `:groundwork-screens/bad-hold`): while held, leaving the page (a link, `:groundwork-screens/go-to`, Back, Forward) asks first. `:ask-with :browser-confirm`: the browser's confirm, its text made when it asks, in the language shown then. `:ask-with [:app/ask-leave]`: the app's own dialog; groundwork-screens stays, holds the move back and dispatches the event with `{:move :text :args}`; the dialog's Leave dispatches `[:groundwork-screens/leave-anyway move]`, which lets go and finishes the move (Back and Forward go back for real); Stay does nothing. Closing the tab asks the browser's own question ([why](decisions/2026-10-23-the-hold-asks-with-the-apps-own-dialog.md)) |
| `show-page` | `[screens/show-page draw-page]`: the page, or the `:placeholder` while the page's `:module` loads (`:load-module!`, on the way in) |
| `mount-root!` | `[root]` drawn into the element with this id once the first catalogs are in (the element keeps its splash until then); again after a save; before `:groundwork-screens/start` it throws, `:groundwork-screens/mounted-before-start` |

Nothing asks before `:groundwork-screens/start`: watch, own, ask-again, once-answered
and send throw `:groundwork-screens/asked-before-start`, naming the messages.

## An orders page

```clojure
(ns shop.browser.orders
  (:require [groundwork-screens.api :as screens] [re-frame.core :as rf]))

;; the start (groundwork's), with what it made
(rf/dispatch-sync [:groundwork-screens/start {:store              store            ; live-updates/make-question-store
                                  :send-write!        send-write!      ; (fn [message {:once-key :keep-alive?}]): a write and its send's options
                                  :retry-writes       {:times 2 :wait-ms 1000}   ; :groundwork-steps/no-answer: sent again, same key
                                  :pages              pages            ; the server's, from the page
                                  :questions-of-pages {:orders/page (fn [{:keys [path-params]}]   ; one for every page
                                                                      [[:orders/of-shop {:shop/id (parse-uuid (:shop-id path-params))}]])
                                                       :about/page  (fn [_] [])}
                                  :actions-question   [:groundwork-access/my-actions {}]   ; groundwork-access's handler; nil: every "can I?" is no
                                  :actions-answer-shape access/my-actions-answer-shape   ; nil with no question
                                  :shaper               shaper               ; the app's
                                  :on-answer-failed   [:shop/answer-failed]  ; nil for none
                                  :on-signed-out      [:shop/to-login]   ; once, with {:page :params :query}; nil for none
                                  :signed-out-reason  :groundwork-access/signed-out   ; the reason it hears; nil with no :on-signed-out
                                  :on-move            nil
                                  :query-shapes-of-pages {:orders/page nil :about/page nil}   ; a malli map reads the query into types
                                  :languages          nil                ; or {:settings settings :language-of-page "en"}
                                  :load-module!       nil                ; or browser-build's, when a page names a :module
                                  :placeholder        nil}])             ; or (fn [page status]) while it loads

(defn orders-page [shop-id]
  (let [{:keys [status data error]} @(rf/subscribe [:groundwork-screens/answer [:orders/of-shop {:shop/id shop-id}]])
        can-move?                   @(rf/subscribe [:groundwork-screens/can-i? :orders/move [:shop shop-id]])]
    (case status
      :loading [:p (screens/tr :orders/loading {})]
      :error   [:p (screens/tr (:groundwork-steps/refused error) (:groundwork-steps/data error))   ; the reason is the key, its data the args
                [:button {:on-click #(rf/dispatch [:groundwork-screens/ask-again [:orders/of-shop {:shop/id shop-id}]])} (screens/tr :orders/try-again {})]]
      [:<>
       (when (= :refresh-error status) [:p.stale (screens/tr :orders/not-refreshed {})])
       (into [:ul]
             (for [{:order/keys [id number stage]} data]
               [:li number " " (name stage)
                (when (and can-move? (= :coating stage))
                  [:button {:on-click #(rf/dispatch [:groundwork-screens/send [:orders/move {:order/id id :order/stage :curing}]
                                                     [:shop/order-moved id]
                                                     {:once-key (screens/make-once-key) :keep-alive? false}])}   ; a click, no draft: a key per click
                   (screens/tr :orders/to-curing {})])]))])))

;; an event stores a text's key and its args, never the text: tr throws outside a view
(rf/reg-event-db :shop/order-moved
                 (fn [db [_ order-id {:keys [status error]}]]
                   (cond-> db
                     (= :error status) (assoc-in [:toasts order-id] {:text (:groundwork-steps/refused error) :args (:groundwork-steps/data error)}))))

;; the view that shows it calls tr, so it is drawn in the page's language, and again when it changes
(defn toast [{:keys [text args]}]
  [:div.toast (screens/tr text args)])

(defn draw-page [{:keys [name path-params]}]
  (case name
    :orders/page [orders-page (parse-uuid (:shop-id path-params))]
    [:p "No such page."]))

(rf/reg-sub :shop/toasts (fn [db _] (:toasts db)))

(defn root []
  [:main [:nav [:a {:href "/about"} (screens/tr :nav/about {})]]   ; a plain link moves in the app
   (into [:aside] (for [[order-id shown] @(rf/subscribe [:shop/toasts])] ^{:key order-id} [toast shown]))
   [screens/show-page draw-page]])

(screens/mount-root! "app" root)
```

The move's answer asks again `[:orders/of-shop …]` (its notes name
`orders`), and groundwork-live-updates rings every other open screen.

## Tests

```sh
npm install && npx shadow-cljs compile test   # on node, over doorbell's real store and a stand-in server
```

Why it is the way it is: [`decisions/`](decisions/README.md).

MIT license.
