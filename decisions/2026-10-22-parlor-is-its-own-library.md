# parlor is its own library: re-frame over doorbell's question store

> **Changed in part** by [The family and its composer are groundwork](https://github.com/p4ulcristian/groundwork/blob/Starting-from-the-bottom/decisions/2026-10-23-the-family-and-its-composer-are-groundwork.md) (2026-10-23): manor, the family, and house, the composer, are both named groundwork now.

> Carries over Groundwork's "re-frame and Reagent in the browser" (2026-10-12, still in force) and the browser half of Groundwork v0.5.0 (`groundwork.endpoints.store`, `groundwork.browser`, `groundwork.browser.routing`), which house's rebuild left out.

**Decision:** the browser's re-frame layer is parlor, a library of its own in its own public repository, under the MIT license, taken from git by tag. It is the room where guests sit and are shown things: what a screen shows, kept in re-frame's db, from doorbell's question store.

- **One public namespace, `parlor.api`** (`.cljs`; parlor has no macros), which registers parlor's events and subscriptions when it loads and has three names of its own: `start-settings-shape`, `actions-answer-shape`, `mount-root!`. Its parts are `parlor.*`: `answers`, `pages`, `can-i`, `start`, `screen`, `settings`.
- **Its events and subscriptions are named `:parlor/…`**: `:parlor/start`, `:parlor/watch`, `:parlor/own`, `:parlor/release`, `:parlor/ask-again`, `:parlor/once-answered`, `:parlor/send`, `:parlor/go-to-page`; `:parlor/answer`, `:parlor/can-i?`, `:parlor/page`. Its inside ones start with a dash (`:parlor/-answered`). Its state is the db's `:parlor`; the app's own is beside it.
- **It keeps no answers of its own making.** doorbell's store asks, keeps answers by question, drops overlapping late answers, and asks again on a change; parlor holds what screens show in re-frame's db and tells doorbell what is watched. The store and the send function are values the start is handed, made by the app (house); parlor makes neither.
- **It needs** re-frame 1.4.7 and Reagent 2.0.1 (the versions Groundwork v0.5.0 had, the newest), doorbell v0.3.0 (the store, its sse handlers), mailroom v0.3.0 (the routing it follows) and malli (the settings). wire comes with mailroom; parlor calls none of it, so it doesn't name it. react and react-dom are the app's `package.json`'s.

**Why its own library:** the store, the routing and the "can I?" are one idea, a screen's data side in re-frame, and none of it needs house: house only puts it together at the start. In its own repository, with a `deps.edn` naming only what it uses, it proves that, and an app on doorbell and mailroom can take it without house. **Why the name:** a parlor is where a guest sits and is shown things; beside the doorbell (a change rings), the mailroom (messages come and go) and the doorman (who may do what), it is the room the person sees.

**The price:** a change to parlor is a commit and a tag in another repository, then a bump in house. Its events are registered globally, as re-frame's always are: one parlor per page.

**Instead of:**
- living in house, as `house.browser.<library>`: house would own a library no other app could take alone, and nothing would prove its deps
- living in doorbell: doorbell is both sides and needs no UI library; re-frame and Reagent would come to every server that takes doorbell
- plain functions over doorbell's watchers, with no re-frame: Groundwork decided re-frame and Reagent, and every app would wire the store into its own state again
