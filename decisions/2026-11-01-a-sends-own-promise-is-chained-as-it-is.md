# A send's own promise is chained as it is, with no extra turn

> Settled while building, 2026-11-01, v0.8.14. Changes in part [A send that throws or fails is a failed answer](2026-11-01-a-send-that-throws-or-fails-is-a-failed-answer.md).

**What was wrong:** v0.8.13 called `:send-write!` inside a promise's constructor, to catch a throw. That put the send's answer one turn later than the send's own promise gives it, always: an app's test harness whose `:send-write!` answers with a promise it settles itself, at once (Iron Rainbow's `support.re-frame`), no longer heard a write's answer in the same step, and its tests of writes failed.

**Decision:** `send-with-retries` chains the promise `:send-write!` returns as it is (`.then` on it, nothing between). A throw is caught around the call and becomes a failed promise; so does a value that isn't a promise (no `then`), `:groundwork-screens/send-without-promise`, which would otherwise throw out of the effect. Both are then answered `:groundwork-steps/failed` and reported, as v0.8.13 decided.

**Why:** catching the throw needs no promise around a send that didn't throw; what the send's promise gives, and when, stays the send's.

**Instead of:**
- **the promise's constructor around every send:** an extra turn for every write, to handle the one that throws
- **`js/Promise.resolve` around the send's value:** the same extra turn
