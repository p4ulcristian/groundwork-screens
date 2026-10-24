# The root is drawn by a function, into an element the app names

> **Changed in part** by [groundwork-screens follows the address bar itself, requires no wire, and keeps Reagent in its views](2026-10-24-groundwork-screens-follows-the-address-bar-and-keeps-reagent-in-its-views.md) (2026-10-24): `mount-root!` is in `groundwork-screens.views`, with everything else Reagent's; `groundwork-screens.screen` is gone.

> Carries over Groundwork v0.5.0's mount in `groundwork.browser/start!` and its `reload!`.

**Decision:** `(parlor/mount-root! "app" root)` draws `[root]` into the page's element with that id, with Reagent, on React's root for that element. The React root is kept on the element itself, so calling it again (after a save in development) reuses it; it forgets re-frame's cached subscriptions first. An id the page has no element for throws `:parlor/no-element`.

**Why:** drawing is not an event: it needs no db and returns nothing to it. Keeping the React root on its element leaves parlor no global of its own, and one page can't make two roots for one element.

**Instead of:** a mount inside `:parlor/start` (the start would need the root and the element, and tests would need a DOM); a `defonce` atom holding the root (a global).
