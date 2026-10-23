# A move says whether it adds a history entry or replaces the one shown

> The lean of Iron Rainbow's port (GROUNDWORK-GAPS.md, G4), mechanical: built without a separate ask, 2026-10-23. Built on tidy-up, not released: parlor and bridge; the composer's part comes later.

**What was wrong:** the customizer rewrites the picked part into the address on every tap (33 places in Iron Rainbow). Every `[:parlor/go-to …]` added a history entry, so Back stepped through every tap. bridge's routing could already replace, but neither `go-to-page!` nor the event could say so.

**Decision:** every move writes out how it goes into the history, a fifth value of the event and a fourth argument of bridge's `go-to-page!`:

```clojure
[:parlor/go-to :customizer/page {} {:part "rim"} :replace]   ; takes the place of the entry shown
[:parlor/go-to :orders/order {:order-id id} {} :push]        ; adds one, which Back returns from
(bridge/go-to-page! :orders/order {:order-id id} {} :push)
```

**Why:** the house rule: every value that sets behaviour is written out. A move that replaces and one that adds are told apart where they are made.

**The price:** every existing go-to gains `:push`.

**Instead of:**
- **a second event, `[:parlor/replace-with …]`**: existing calls stay as they are, but two names for one move
- **`:push` when the way is left out**: a hidden default

## Settled while building

Choices made while building, not asked:

1. **Any other way throws,** `:bridge/bad-way` from bridge, before anything moves; parlor passes the way on unchecked, so the one check is bridge's.
2. **A move to the address shown replaces either way,** as before: `:push` there adds no entry that Back would return to the same page from.
3. **A link a person clicks is `:push`,** as a browser's own link is.
4. **`:replace` still asks `:may-leave?`** when it leads to another page: replacing the entry doesn't keep the unsaved changes.
