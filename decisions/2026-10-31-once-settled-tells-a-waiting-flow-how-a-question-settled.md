# once-settled tells a waiting flow how a question settled, a refusal too

> The user's decision, 2026-10-31, after seeing an example. Adds to [Owners hold questions](2026-10-22-owners-hold-questions.md) and [parlor uses its libraries' own functions; once-answered waits for an answer on its way](2026-10-23-parlor-uses-its-libraries-own-functions.md); `once-answered` stays as it is.

**What was wrong:** a flow that reads a question once and continues from the answer needs to hear a refusal too. Iron Rainbow's service-area wizard reads the shop's partnership request: a 404 means "this shop hasn't booked yet", and the wizard goes on from there. The Flex shell reads the workspace: a 403 or a 404 means "back to the picker". `once-answered` tells only good data, and `:on-answer-failed` is the app's one hook for every question, not the waiting flow's.

**Decision:** one event beside `once-answered`, which tells `event` once, as its last argument, how the held question settled, in the shape `:groundwork-screens/send`'s answer has:

```clojure
(rf/dispatch [:groundwork-screens/once-settled [:partnerships/of-shop {:shop/id id}] [:wizard/continue]])

(rf/reg-event-fx :wizard/continue
  (fn [{:keys [db]} [_ {:keys [status data error]}]]
    (if (= :ready status)
      {:db (assoc-in db [:wizard :partnership] data)}
      {:db (assoc-in db [:wizard :step] (if (= :partnership/not-found (:groundwork-steps/refused error)) :book :failed))})))
;; {:status :ready :data …} or {:status :error :error {:groundwork-steps/refused … :groundwork-steps/data …}}
```

- **Now,** when the entry has settled and no fresher answer is on its way (the same test as `once-answered`'s, groundwork-live-updates's `is-question-being-asked?` and the kept answer): `:ready` tells `{:status :ready :data …}`; `:error` tells `{:status :error :error …}`; `:refresh-error` tells `{:status :error :error …}` too: the latest answer was a refusal, so that is how it settled.
- **Otherwise** (loading, or a fresher answer coming) it waits, and the next answer that lands for the question tells it, good or refused.
- **A question nothing holds tells nothing,** as with `once-answered`: the caller watches first. A waiter whose question is let go before it settles is dropped.
- **It throws before `:groundwork-screens/start`,** `:groundwork-screens/asked-before-start`, naming "once-settled", as the other events that ask do.

**Why:** the flow that waits is the one that knows what a refusal means for it, so it hears the refusal where it waits, in one event, in the shape a write's answer already has. `once-answered` keeps its promise (good data or nothing) for the forms that fill from it.

**Instead of:**
- **(B) built in each app from `once-answered` and `:on-answer-failed`:** one idea split over two hooks, and the app keeps who waits for a refusal
- **(C) the read sent as a write (`:groundwork-screens/send`):** a read isn't a write: it would carry a once-key, be retried as a write is, and its tables would be noted as written

## Settled while building

Choices made while building, not asked:

1. **The two kinds of waiter are kept apart:** `once-answered`'s under `:waiting`, `once-settled`'s under `:settling`. An answer empties both; letting a question go drops both.
2. **One function makes the told shape,** `make-settled-answer`, used by `once-settled` and by `:groundwork-screens/send`'s event, so the two can't drift apart.
3. **`:error` with a fresher answer coming waits** (a change heard asks it again while it still reads as an error), where `once-answered` tells nothing for an `:error` entry.
