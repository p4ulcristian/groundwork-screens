# A page has its query and language, scrolls once drawn, holds unsaved changes, and loads its module on the way in

> The user's decision, 2026-10-23 (house's "routing extras"). Since v0.5.0. Takes mailroom v0.6.0 (a page has its query, #fragment and language; a move says how to scroll; leaving is asked) and dictionary v0.1.0. Changes in part [A page is re-frame data, owns the questions it declares, and moving is an event](2026-10-22-a-page-is-re-frame-data-owns-its-questions-and-moving-is-an-event.md) (`:parlor/go-to` takes a query) and [Every start setting is required](2026-10-22-every-start-setting-is-required.md) (four new settings). Takes back the routing rows of [What parlor leaves out](2026-10-22-what-parlor-leaves-out.md): languages, scrolling, the unsaved guard, loading on demand and query filters. Carries over Groundwork v0.5.0's `groundwork.browser` and `groundwork.browser.routing`.

**Decision:** the re-frame side of routing is parlor's; the address, the history and the scroll positions are mailroom's; the language rules dictionary's; the module loading showroom's.

- **The page**, `@(rf/subscribe [:parlor/page])`, is mailroom's: `{:name :page :path-params :query :query-errors :fragment :language}`. The query's values are text, or real types when the page declares a query shape: the start's `:query-shapes-of-pages`, every page and no other, a malli map or nil.
- **`[:parlor/go-to name path-params query]`** takes a query, `{}` for none, and keeps the language. A change of only the query stays on the page: its questions are owned again, so what it still asks is kept and not asked again, the new ones asked; it isn't leaving, so nothing is asked of the person; the scroll stays.
- **Languages**: the start's `:languages`, nil or `{:dictionary settings :language-of-page "en"}`; parlor hands mailroom dictionary's functions over those settings.
- **Scrolling once drawn**: mailroom says how a move scrolls; parlor does it after Reagent's next drawing (`after-render`), and for a page whose module is loading, after the module.
- **Unsaved changes**: `[:parlor/hold "Leave without saving?"]` while a form is dirty, `[:parlor/let-go]` after saving. While held, a move to another page (a link, `:parlor/go-to`, Back and Forward) asks the person the message, with the browser's confirm: yes lets go and moves, no stays where it is (Back put back). Closing or reloading the tab asks the browser's own question (`beforeunload`; browsers show their own words there).
- **Loading on demand**: a page the server names a `:module` for has it loaded on the way in, through the start's `:load-module!` (showroom's: its stylesheets, then its code), once. Its questions are owned at once, so its data loads beside its code. `[parlor/show-page draw-page]` draws `[draw-page page]`, or, while the module loads, the start's `[placeholder page :loading]` (`:failed` when it couldn't load).
- **The start's settings** gain `:query-shapes-of-pages`, `:languages`, `:load-module!` and `:placeholder`, every key required; the last two may be nil only when no page names a module.

```clojure
(rf/dispatch [:parlor/go-to :orders/list {} {:stage :curing}])
(rf/dispatch [:parlor/hold "Leave without saving?"])
(defn root [] [:main [nav] [parlor/show-page draw-page]])
```

**Why:** each piece where it is known: mailroom knows which move it was and where an entry was left, parlor knows when the page is drawn and what the person holds, dictionary knows the languages, showroom knows the build. The confirm is the browser's own question with the app's words: no screen of the app has to show a dialog for every form, and Back is caught the same as a link. Data and code load side by side: a module's page shows as soon as both are there.

**The price:** every page writes out its query shape, nil for most. The question before leaving is the browser's confirm, plain and blocking; an app wanting its own dialog has none. A page opened fresh on a module's address shows the placeholder while its module loads.

**Instead of:**
- the app showing its own "save or discard?" for a held move (v0.5.0's `::held-move`, `continue!`, `stay!`): three more names, and every app writes the dialog
- the placeholder drawn in place of the whole root: the navigation would blink away while a module loads
- the scroll right after the move: the page isn't drawn yet, and a restored position would be cut short
