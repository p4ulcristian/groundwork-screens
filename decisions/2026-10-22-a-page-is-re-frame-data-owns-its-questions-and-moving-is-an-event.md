> **Changed in part** by [A page has its query and language, scrolls once drawn, holds unsaved changes, and loads its module on the way in](2026-10-23-a-page-has-its-query-and-language-scrolls-once-drawn-holds-unsaved-changes-and-loads-its-module.md) (2026-10-23, v0.5.0): `:parlor/go-to` takes a query; a page has its query, language and `#fragment`.

# A page is re-frame data, owns the questions it declares, and moving is an event

> The user's decision, 2026-10-22. Changes in part [The page is re-frame data, from mailroom's routing](2026-10-22-the-page-is-re-frame-data.md), [Owners hold questions; a page owns its questions](2026-10-22-owners-hold-questions.md), [Every start setting is required](2026-10-22-every-start-setting-is-required.md) and [Nothing asks before the start](2026-10-22-nothing-asks-before-the-start.md): `:questions-of-page`, one function, is now `:questions-of-pages`, one declaration per page; `:parlor/go-to-page` is `:parlor/go-to`. Since v0.2.0.

> Carries over Groundwork's "a screen says what it needs by entering a route" (a route's `:reads`, owned by `:groundwork/route`).

**Decision:**

- **The current page is re-frame data**: `@(rf/subscribe [:parlor/page])` is `{:name :page :path-params :query}`, mailroom's page, or nil for an address no page has.
- **Moving is an event**: `(rf/dispatch [:parlor/go-to :orders/order {:order-id id}])`, mailroom's `go-to-page!` underneath.
- **A page declares its questions**: the start's `:questions-of-pages` has, for every page in `:pages` and no other, `(fn [page]) -> questions`, `(fn [_] [])` for a page that needs none. A page in `:pages` without one, or one for a page `:pages` doesn't have, refuses the start, `:parlor/bad-settings`, with every other problem at once.
- **Entering a page owns its questions; leaving releases them**: the owner `:parlor/page` owns exactly the entered page's questions, so what the next page shares is kept and not asked again, and the rest is let go.

```clojure
:questions-of-pages {:orders/page  (fn [{:keys [path-params]}] [[:orders/of-shop {:shop/id (parse-uuid (:shop-id path-params))}]])
                     :orders/order (fn [{:keys [path-params]}] [[:orders/one {:order/id (parse-uuid (:order-id path-params))}]])
                     :about/page   (fn [_] [])}
```

**Why:** a screen says what it needs by entering a page, with no code in the screen: the page is data like any other in the db, every screen and event reads it, and a move is an event an event handler can return after a save. One declaration per page, checked against the server's pages, means a new page can't be forgotten: it refuses the start instead of showing a screen that waits for data nothing asked.

**The price:** every page writes out its questions, `(fn [_] [])` included. A page's questions are functions beside the pages, not on them: the pages are the server's JSON, which carries no functions.

**Instead of:** parlor only answering questions, and the app gluing mailroom's `on-page` to its own events: every app would write the same glue (put the page in the db, own its questions, release the old ones), and a forgotten release keeps old pages' questions watched.
