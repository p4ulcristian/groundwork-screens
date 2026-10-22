# An answer has four statuses; a failed refresh keeps the data

> Carries over Groundwork v0.5.0's `::store/answer` and doorbell's [The browser keeps answers by question](https://github.com/p4ulcristian/doorbell/blob/main/decisions/2026-10-12-the-browser-keeps-answers-by-question.md).

**Decision:** `@(rf/subscribe [:parlor/answer question])` is one of

```clojure
{:status :loading}                              ; nothing yet, or asked again after a failed first answer
{:status :ready :data …}
{:status :error :error answer}                  ; failed, and no good data before it
{:status :refresh-error :data old :error answer} ; a fresh answer failed; the last good data stays
```

- **A failure** is what mailroom answers instead of data: a map of only `:error` (a refusal, "Not found.", "Too many requests.", "Something went wrong."), a map of only `:errors` (params that didn't fit), or an answer carrying `:mailroom/errors` (one that didn't fit its shape), and doorbell's `{:error "No answer from the server."}`. Anything else is data.
- **`:refresh-error` is a status**, not a key on `:ready` as in v0.5.0: a screen that shows only `:ready` data would otherwise show stale data as fresh; with its own status it says so, and a screen that doesn't care reads `:data` for both.
- **parlor's entry lives while an owner holds the question.** When the last lets go, parlor drops it, and doorbell keeps the answer for its `:keep-unwatched-answers-ms`: held again, doorbell gives the kept answer at once, then the fresh one.
- **Every failed answer to a held question** is dispatched to the start's `:on-answer-failed` event, with the question and the answer, when the app names one (v0.5.0's `:on-read-failed`): a toast, a sign-in.

**Why:** a screen draws from one map whatever happened; good data never vanishes because a refresh failed, and the screen can still tell it is stale. Dropping the entry when nothing holds it leaves one cache, doorbell's, with one time limit.

**The price:** v0.5.0's screens that read `:refresh-error` beside `:ready` read the status instead. A screen held again after a failed refresh starts from doorbell's kept answer, the failure, not the older good data.

**Instead of:**
- v0.5.0's `:ready` with a `:refresh-error` key: stale data reads as ready
- parlor keeping entries after release with its own time limit: two caches, two clocks
- a failure as an exception or a rejected promise: an answer is data either way, and the screen draws both
