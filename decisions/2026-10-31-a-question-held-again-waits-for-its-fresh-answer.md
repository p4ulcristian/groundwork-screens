# A question held again: its kept answer is shown, and the waiters wait for the fresh one

> Settled while fixing a bug, 2026-10-31, found by Iron Rainbow's browser tests. Adds a sentence to [once-settled tells a waiting flow how a question settled](2026-10-31-once-settled-tells-a-waiting-flow-how-a-question-settled.md) and to [parlor uses its libraries' own functions; once-answered waits for an answer on its way](2026-10-23-parlor-uses-its-libraries-own-functions.md); what they promise is unchanged.

**What was wrong:** a question let go and held again (`own`, `watch`) is told groundwork-live-updates's kept answer (`:keep-unwatched-answers-ms`) at once by `watch-question!`, which then asks it afresh. groundwork-screens took that kept answer as any answer: it reached groundwork-screens as a queued event, so a flow that held the question and waited in the same step (`once-settled`, `once-answered`) found the entry loading, waited, and was told the kept answer when the queued event came, with the fresh ask already out. A flow that holds a question again to read it fresh ("check the slug again", "poll the 3D design", "ask the ZIP again") got the old answer.

**Decision:** the kept answer told when a question is held again is shown, not told: the entry takes it (the screen keeps the old data while the fresh answer comes, its status as it would be), and nobody waiting is told it, since watching asks the question afresh, so a fresher answer is always on its way. The waiters are told the next answer that comes after it. Also, an answer told of an earlier hold of the same question, its event still queued when the question was let go and held again, is taken by nothing: the hold's own kept answer shows the same data.

```clojure
(rf/reg-event-fx :slug/check-again
  (fn [_ [_ slug]]
    {:fx [[:dispatch [:groundwork-screens/release :slug-check]]
          [:dispatch [:groundwork-screens/watch :slug-check [[:shops/slug-free {:slug slug}]]]]
          [:dispatch [:groundwork-screens/once-settled [:shops/slug-free {:slug slug}] [:slug/checked]]]]}))
;; :slug/checked gets the fresh answer; the kept one is shown meanwhile
```

Unchanged: a held question with a settled answer and nothing asked tells at once; while a fresher answer is on its way (`is-question-being-asked?`, or the kept answer newer than the entry's) the waiter waits.

**How:** each hold of a question is numbered (`:hold`, counted from the start on, across starts). The watch's `on-answer` dispatches `[:groundwork-screens/-answered question answer {:hold n :kept? bool}]`: `:kept? true` for the call inside `watch-question!` itself, which groundwork-live-updates makes with the kept answer, now, before it asks ("with the kept answer now, if any"). An answer whose hold is not the entry's is not taken.

**Why here, not in groundwork-live-updates:** groundwork-live-updates does what it says: it tells a new watcher the kept answer now and asks afresh, and `is-question-being-asked?` is true from then on. The wrong step was groundwork-screens's, which treated the kept answer as a fresh one for its waiters.

**Instead of:**
- **(B) skip an answer identical to the one the entry shows:** the entry is loading after the hold, so there is nothing to compare; and groundwork-live-updates's no-answer refusal is one value, so two failures in a row are identical
- **(C) check `is-question-being-asked?` when any answer lands:** a fresh answer with a change heard during its ask would wait for the next one too, where the waiter is promised the next answer
- **(D) a second argument to groundwork-live-updates's `on-answer`, kept or fresh:** a change to its api for what groundwork-screens can tell itself from the documented order
