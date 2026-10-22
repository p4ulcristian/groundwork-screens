# The page is re-frame data, from mailroom's routing

> Changes in part Groundwork's "Routing is Groundwork's, and a route is a record": the routing is mailroom's now; parlor puts its page in re-frame.

**Decision:** `:parlor/start` follows the address bar with mailroom's `start-following-address!` and the start's `:pages` (the server's, from the page). On every move:

- the db's `:parlor :page` becomes mailroom's page, `{:name :page :path-params :query}`, or nil for an address no page has; `@(rf/subscribe [:parlor/page])` reads it;
- the owner `:parlor/page` owns exactly the page's questions;
- the start's `:on-move` event, when the app names one, gets `{:from page :to page}`.

`[:parlor/go-to-page name path-params]` moves (mailroom's `go-to-page!`), from an event that has saved and goes on. A link's address is mailroom's `make-address-of-page`. The tab's title is mailroom's (the page's `:title`).

The root screen picks the screen by the page's `:name`: the page is the server's JSON and carries no component, so v0.5.0's `screen` component, which read a route's `:view`, is the app's own `case` now.

**Why:** a page in the db is a value every screen and event reads like any other; the questions follow it with no code in the screens.

**The price:** v0.5.0's routing did more, and mailroom's doesn't yet: languages in the address, scrolling on a move, the unsaved-changes guard, code loaded on demand with a placeholder, `href` and moves by address, query filters (`push-query!`, `replace-query!`). Before the first move is taken in, the page is nil, as for an unknown address.

**Instead of:**
- parlor routing on its own, over reitit: two routers in one page, and mailroom's pages are the server's already
- the app's `on-page` callback dispatching by hand: every app would write the same event
