# The unsaved-changes hold asks with the app's own dialog

> The user's decision, 2026-10-23, from Iron Rainbow's port (GROUNDWORK-GAPS.md, G6: a dirty form opens IR's own dialog). Adds to [tr is called only in views](2026-10-23-tr-is-called-only-in-views.md) (the hold takes a key). Not built yet.

**Decision:** the hold writes out how it asks:

```clojure
[:parlor/hold {:ask-with [:flex.batches/ask-leave] :text :batches/unsaved :args {}}]   ; the app's dialog
[:parlor/hold {:ask-with :browser-confirm        :text :batches/unsaved :args {}}]     ; the browser's confirm
```

With an event, parlor holds the move back (a link, `go-to`, Back or Forward), dispatches the event with the move, and stays where it is. The dialog's Leave button dispatches `[:parlor/leave-anyway move]`: parlor finishes the move as it would have been (Back and Forward go back for real, adding no history entry). Stay does nothing. Closing the tab or reloading still shows the browser's own question, which no page can replace.

**Why:** the dialog looks like the rest of the app, and every way of leaving inside the app is caught the same.

**The price:** bridge's routing holds a move back and finishes it later.

**Instead of:** keeping only the browser's `confirm()`: it looks unlike the app.
