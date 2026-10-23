# A held answer is read inside an event handler by `find-answer`

> The lean of Iron Rainbow's port (GROUNDWORK-GAPS.md, G9), mechanical: built without a separate ask, 2026-10-23. Built on tidy-up, not released: parlor.

**What was wrong:** Iron Rainbow reads a held answer inside an event handler in 89 places in 48 files, `(query/data db q)`. parlor had only the `[:parlor/answer q]` subscription, which a handler must not read, and the later `[:parlor/once-answered q event]`. The path `[:parlor :answers q]` is parlor's own, not public.

**Decision:** a public pure function, which gives what the subscription gives:

```clojure
(rf/reg-event-fx :orders/print
  (fn [{:keys [db]} [_ order-id]]
    (let [{:keys [status data]} (parlor/find-answer db [:orders/one {:order/id order-id}])]
      (when (= :ready status) {:app/print data}))))
;; {:status :loading}, {:status :ready :data …}, {:status :error :error refusal}, {:status :refresh-error :data old :error refusal}
```

**Why:** one call, pure, testable with a plain db; the handler sees exactly what the screen shows.

**The price:** a handler that needs the next answer, not the one held now, still waits with `:parlor/once-answered`.

**Instead of:**
- **a coeffect**: its argument is fixed when the handler is registered, but Iron Rainbow's question depends on the event's own arguments
- **`once-answered` only**: each of the 89 places becomes two events

## Settled while building

Choices made while building, not asked:

1. **It reads only:** a question nothing holds is `{:status :loading}`, as the subscription gives it, and nothing is asked; before `:parlor/start` too, since reading asks nothing (watch, own, ask-again, once-answered and send still throw then).
2. **The owners are left out,** as from the subscription: who holds a question is parlor's own.
3. **The subscription and `find-answer` read an entry through one function,** so they can't drift apart.
