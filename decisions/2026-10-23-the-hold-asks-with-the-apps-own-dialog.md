# The unsaved-changes hold asks with the app's own dialog

> The user's decision, 2026-10-23, from Iron Rainbow's port (GROUNDWORK-GAPS.md, G6: a dirty form opens IR's own dialog). Adds to [tr is called only in views](2026-10-23-tr-is-called-only-in-views.md) (the hold takes a key). Built on tidy-up, not released: parlor and bridge; the composer's part comes later.

**Decision:** the hold writes out how it asks:

```clojure
[:parlor/hold {:ask-with [:flex.batches/ask-leave] :text :batches/unsaved :args {}}]   ; the app's dialog
[:parlor/hold {:ask-with :browser-confirm        :text :batches/unsaved :args {}}]     ; the browser's confirm
```

With an event, parlor holds the move back (a link, `go-to`, Back or Forward), dispatches the event with the move, and stays where it is. The dialog's Leave button dispatches `[:parlor/leave-anyway move]`: parlor finishes the move as it would have been (Back and Forward go back for real, adding no history entry). Stay does nothing. Closing the tab or reloading still shows the browser's own question, which no page can replace.

**Why:** the dialog looks like the rest of the app, and every way of leaving inside the app is caught the same.

**The price:** bridge's routing holds a move back and finishes it later.

**Instead of:** keeping only the browser's `confirm()`: it looks unlike the app.

## Settled while building

Choices made while building, not asked:

1. **The app's event gets `{:move move :text key :args args}`,** conj'ed onto it: the dialog's view shows the text with `(parlor/tr text args)`, and its Leave hands the move back, `[:parlor/leave-anyway move]`.
2. **A move is bridge's data, every key written out:** `{:address "/orders/7" :way :push :index nil}`; `:way` is `:push` or `:replace` for a link or `:parlor/go-to`, `:history` for Back and Forward, whose `:index` is the history entry it goes to. bridge's `finish-move!` refuses anything else, `:bridge/bad-move`.
3. **bridge's `:may-leave?` is asked with the move,** `(fn [move])`; false stays, and `(bridge/finish-move! move)` finishes it later without asking again. A link finishes to its address as `:push` (or `:replace`); Back or Forward goes to its entry with `history.go`, and its pop is let through unasked.
4. **Leave pressed while Back's address is still being put back** (the browser's pop of the put-back hasn't come) goes once it has, not before, so the history ends where Back would have taken it.
5. **`[:parlor/leave-anyway move]` lets go of the hold first,** so the finished move, and closing the tab after it, ask nothing; Stay dispatches nothing, and the hold stays.
6. **`:ask-with` is required in every hold,** `:browser-confirm` or an event; a hold without it is refused, `:parlor/bad-hold`.
