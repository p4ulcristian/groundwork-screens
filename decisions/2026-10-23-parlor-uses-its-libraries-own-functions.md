> **Changed in part** by ["Can I?" checks the answer by the shape it is handed](2026-10-23-can-i-checks-the-answer-by-the-shape-it-is-handed.md) (2026-10-23, v0.6.0): parlor has no `actions-answer-shape`; the start takes doorman's as `:actions-answer-shape`, and `:wire`.

# parlor uses its libraries' own functions; once-answered waits for an answer on its way; "can I?" reads doorman's message

> Changes in part [An answer has four statuses](2026-10-22-an-answer-has-four-statuses.md), [Owners hold questions](2026-10-22-owners-hold-questions.md), [A write's answer asks again what its notes name](2026-10-22-a-write-s-answer-asks-again-what-its-notes-name.md) and ["Can I?" reads an answer the app names](2026-10-22-can-i-reads-an-answer-the-app-names.md). On doorbell v0.4.0 and mailroom v0.4.0. Since v0.3.0.

**Decision:**

- **A failure is mailroom's `is-error-answer?`**: the same three shapes as before (`{:error …}` alone, `{:errors …}` alone, an answer carrying `:mailroom/errors`), doorbell's `{:error "No answer from the server."}` among them, now from the one definition both of mailroom's sides use, not a copy in parlor.
- **A write's good answer is told to doorbell with `hear-changed-table!`**, one call per table its notes name, not through the sse line's handler taken out of `make-sse-handlers`.
- **`[:parlor/once-answered question event]` waits for an answer on its way.** With good data there (`:ready` or `:refresh-error`), it tells `event` at once only when no fresher answer is coming; otherwise it waits for the next good answer, as while loading. A fresher answer is coming when doorbell says the question is being asked (`is-question-being-asked?`: out now, or a gathered change, a write's included, will ask it), or when doorbell's kept answer is not the one parlor's entry was made from (it has landed and its event is still queued). As before, a failed answer, or the question let go, tells it nothing.
- **The `:actions-question` is doorman's message**: doorman v0.4.0's `make-my-actions-handler` answers `actions-answer-shape`, open actions included under nil kind and id; house names it `:doorman/my-actions`, so a start writes `:actions-question [:doorman/my-actions {}]`. parlor doesn't take doorman, a server library: it reads the answer by its shape.

```clojure
(rf/dispatch [:parlor/send [:orders/move {:order/id id :order/stage :curing}] [:order-form/moved]])
;; in :order-form/moved
{:fx [[:dispatch [:parlor/once-answered [:orders/one {:order/id id}] [:order-form/fill]]]]}
;; :order-form/fill gets the order as it is after the move, not the one shown before it
```

**Why:** a library on top that copies another's rules goes wrong unseen when they change; parlor's report named three such places. `once-answered` is mostly called right after a save, the one moment the data there is about to be replaced: telling it at once filled a form with what the save changed.

**The price:** `once-answered` reads doorbell's store from its event, through a coeffect (`:parlor/-newer-coming`). A question asked again and again (a busy table) can keep it waiting through several answers; a failed one in between drops it.

**Instead of:**
- `once-answered` telling the data there, as v0.2.0 did: wrong after every save
- keeping parlor's own "asked" flag: doorbell asks on its own (a change, a missed line), and parlor would have to be told each time
