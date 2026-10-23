# tr is the one text call in the browser, and it is called only in views

> The user's decision, 2026-10-23, from the tidy-up. Built on tidy-up, not released.

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

## Settled while building

Choices made while building, not asked:

1. **The error is `:parlor/tr-outside-a-view`,** `{:type :parlor/tr-outside-a-view :key key}`; its message says what to do instead (store the key and its args, the view calls `tr`).
2. **"While a view draws" is Reagent's reactive context** (`reagent.ratom/reactive?`): a component drawing, or a reaction it reads. A subscription's own function runs in one too, so `tr` works there; its text is still subscribed, so it follows the language.
3. **The subscription is renamed `[:parlor/-text key args]`,** parlor's inside name (the `-` the api-names test leaves out), not deleted: `tr` subscribes it. `[:parlor/texts-ready?]` stays public.
4. **`tr` drawn outside the root `mount-root!` draws** is nil until the first catalogs are in, as the subscription was; inside it, never.
5. **Before `:parlor/start` and without `:languages`,** `tr` in a view is the key's name, as before.
6. **parlor pins bridge and dictionary at their new tidy-up commits** (bridge `63451ad…`, dictionary `38014ef…`); dictionary's browser `make-text` stays public for `tr`.
7. **The README's orders page draws every text with `tr`,** and its `:shop/order-moved` stores a refusal's key and data for a toast view.

Left for later, outside parlor: the composer reads `[:parlor/text …]` in `test-code/groundwork_test/sample/browser.cljs` (three), `sample/reports.cljs` (one) and `test-code/groundwork/browser/build_check.cljs` (one), and names it in `README.md`; each becomes `(parlor/tr key args)` in its view.

Open, not built: `[:parlor/hold message]` still takes a text, which the view makes with `tr` and an event handler stores until the page is let go (the composer's sample does so); a language switch while it is held keeps the old text. Whether it takes a key and its args instead is the user's to decide.
