# parlor

The room where guests sit and are shown things: the browser's screens over
[doorbell](https://github.com/p4ulcristian/doorbell)'s question store, in
[re-frame](https://github.com/day8/re-frame), drawn with
[Reagent](https://github.com/reagent-project/reagent). A screen says which
questions it shows; parlor keeps their answers in re-frame's db with one of
four statuses, has doorbell watch them (asked again when a table they read
changes), sends writes and asks again what they touched, says what the
person may do, and puts the page the address bar leads to
([mailroom](https://github.com/p4ulcristian/mailroom)'s routing) in the db.

```clojure
io.github.p4ulcristian/parlor {:git/tag "v0.7.5" :git/sha "4e8b436"}
```

Needs re-frame 1.4.7, Reagent 2.0.1, doorbell, mailroom, dictionary and malli; react
and react-dom come from the app's `package.json`. Browser only, one public
namespace, `parlor.api`, which registers the events and subscriptions
below when it loads. parlor makes no store, no send function and no wire:
the app (groundwork) makes them and hands them to the start.

## The api

| name | does |
|---|---|
| `[:parlor/start settings]` | once; every setting required (`start-settings-shape`), checked first; follows the address bar |
| `[:parlor/watch owner questions]` `[:parlor/own owner questions]` `[:parlor/release owner]` | an owner holds questions: adds them, exactly these (shared ones kept), none |
| `[:parlor/answer question]` (sub) | `{:status :loading}`, `:ready` with `:data`, `:error` with `:error`, `:refresh-error` with the old `:data` and the `:error` |
| `[:parlor/ask-again question]` | a held question asked again now |
| `[:parlor/once-answered question event]` | `event` gets the data once: now, or, while a fresher answer is on its way (doorbell's `is-question-being-asked?`) or none is there, with the next good answer |
| `[:parlor/send [name params] event]` | a write, through `:send-write!` with a one-time key made for this send, sent again with the same key while it gets no answer (`:retry-writes`); `event` gets `{:status :ready :data …}` or `{:status :error :error …}`; a good answer asks again what its notes name |
| `[:parlor/can-i? action scope]` (sub) | from the `:actions-question`'s answer, checked by `:actions-answer-shape` (doorman's `my-actions-answer-shape`) through the app's `:wire`; `scope` is `[kind id]` or a list of them |
| `[:parlor/page]` (sub) `[:parlor/go-to name path-params query]` | the page, `{:name :page :path-params :query :query-errors :fragment :language}` or nil; a move, keeping the language. Entering a page owns the questions it declares (`:questions-of-pages`), leaving releases them; a change of only the query keeps what it still asks, and the scroll. Each move scrolls once drawn: top, the `#fragment`, or where Back left it |
| `[:parlor/text key args]` (sub) `[:parlor/texts-ready?]` (sub) | a message in the page's language (dictionary's catalogs, fetched by parlor; the fallback's, else the key's name); a swapped catalog draws again; nil until the first is in |
| `[:parlor/hold message]` `[:parlor/let-go]` | unsaved changes: while held, leaving the page asks the person the message (the browser's confirm), closing the tab the browser's own question |
| `show-page` | `[parlor/show-page draw-page]`: the page, or the `:placeholder` while the page's `:module` loads (`:load-module!`, on the way in) |
| `mount-root!` | `[root]` drawn into the element with this id; again after a save |

Nothing asks before `:parlor/start`: watch, own, ask-again, once-answered
and send throw `:parlor/asked-before-start`, naming the messages.

## An orders page

```clojure
(ns shop.browser.orders
  (:require [parlor.api :as parlor] [re-frame.core :as rf]))

;; the start (groundwork's), with what it made
(rf/dispatch-sync [:parlor/start {:store              store            ; doorbell/make-question-store
                                  :send-write!        send-write!      ; (fn [message once-key]): a write and its one-time key
                                  :retry-writes       {:times 2 :wait-ms 1000}   ; no answer: sent again, same key
                                  :pages              pages            ; the server's, from the page
                                  :questions-of-pages {:orders/page (fn [{:keys [path-params]}]   ; one for every page
                                                                      [[:orders/of-shop {:shop/id (parse-uuid (:shop-id path-params))}]])
                                                       :about/page  (fn [_] [])}
                                  :actions-question   [:doorman/my-actions {}]   ; doorman's handler; nil: every "can I?" is no
                                  :actions-answer-shape doorman/my-actions-answer-shape   ; nil with no question
                                  :wire               wire               ; the app's
                                  :on-answer-failed   [:shop/answer-failed]  ; nil for none
                                  :on-move            nil
                                  :query-shapes-of-pages {:orders/page nil :about/page nil}   ; a malli map reads the query into types
                                  :languages          nil                ; or {:dictionary settings :language-of-page "en"}
                                  :load-module!       nil                ; or showroom's, when a page names a :module
                                  :placeholder        nil}])             ; or (fn [page status]) while it loads

(defn orders-page [shop-id]
  (let [{:keys [status data error]} @(rf/subscribe [:parlor/answer [:orders/of-shop {:shop/id shop-id}]])
        can-move?                   @(rf/subscribe [:parlor/can-i? :orders/move [:shop shop-id]])]
    (case status
      :loading [:p "Loading orders…"]
      :error   [:p (:error error) [:button {:on-click #(rf/dispatch [:parlor/ask-again [:orders/of-shop {:shop/id shop-id}]])} "Try again"]]
      [:<>
       (when (= :refresh-error status) [:p.stale "Couldn't refresh; showing the last orders."])
       (into [:ul]
             (for [{:order/keys [id number stage]} data]
               [:li number " " (name stage)
                (when (and can-move? (= :coating stage))
                  [:button {:on-click #(rf/dispatch [:parlor/send [:orders/move {:order/id id :order/stage :curing}]
                                                     [:shop/order-moved]])}
                   "To curing"])]))])))

(defn draw-page [{:keys [name path-params]}]
  (case name
    :orders/page [orders-page (parse-uuid (:shop-id path-params))]
    [:p "No such page."]))

(defn root []
  [:main [:nav [:a {:href "/about"} "About"]]   ; a plain link moves in the app
   [parlor/show-page draw-page]])

(parlor/mount-root! "app" root)
```

The move's answer asks again `[:orders/of-shop …]` (its notes name
`orders`), and doorbell rings every other open screen.

## Tests

```sh
npm install && npx shadow-cljs compile test   # on node, over doorbell's real store and a stand-in server
```

Why it is the way it is: [`decisions/`](decisions/README.md).

MIT license.
