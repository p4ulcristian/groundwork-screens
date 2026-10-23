# tr is the one text call in the browser, and it is called only in views

> The user's decision, 2026-10-23, from the tidy-up. Not built yet.

**Decision:** `(parlor/tr key args)` is the one public text call in the browser, and it works only while a view draws: it subscribes, so the view redraws when the language or the catalog changes. Called anywhere else (an event handler, an effect) it throws, named. The `[:parlor/text key args]` subscription leaves the public api. An event that needs a text stores its key and data instead, and the view that shows it calls `tr`:

```clojure
;; an event: no text, a key and its data
{:db (assoc-in db [:toasts id] {:text :order/already-shipped :args {:order/shipped-at t}})}

;; the toast's view
(defn toast [{:keys [text args]}] [:div.toast (parlor/tr text args)])
```

**Why:** texts are drawn only in views, so a text is always in the page's current language; `tr` no longer behaves two ways depending on where it runs.

**The price:** events that built texts (toasts, confirms) store keys and data.

**Instead of:** `tr` reading once outside a view and subscribing inside: the same call, two behaviours, and a text frozen in the old language after a switch.
