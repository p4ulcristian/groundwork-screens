# A send that throws or fails is a failed answer: the write's event runs, and the bug is reported

> The user's decision, 2026-11-01 (the libraries never hang a form again). Since v0.8.13. Changes in part [A write carries a one-time key, kept across its retries](2026-10-23-a-write-carries-a-one-time-key-kept-across-its-retries.md): a failed promise was passed on. Goes with groundwork-bridge's [Params that don't fit are answered `:groundwork-steps/invalid` in the browser, not thrown](https://github.com/p4ulcristian/groundwork-bridge/blob/main/decisions/2026-11-01-params-that-dont-fit-are-answered-invalid-in-the-browser-not-thrown.md).

**What was wrong:** `:groundwork-screens/send`'s effect waited only for an answer. A `:send-write!` that threw (groundwork-bridge's `send-message!` threw for params that didn't fit their shape, before any promise existed) or whose promise failed left the write's event unrun: an uncaught error, and the form "saving" for good. Found on Iron Rainbow's setup wizard.

**Decision:** a send that throws, or whose promise fails, is answered in the browser `{:groundwork-steps/refused :groundwork-steps/failed :groundwork-steps/data nil}` (`send-failed`), so its event gets `{:status :error :error send-failed}` and the page's own failure handling runs:

- the throw is caught where the send starts (`send-with-retries` calls `:send-write!` inside a promise's constructor), on the first try and on every retry; `send-with-retries`'s promise never fails;
- what was thrown, or the promise failed with, is reported as the browser reports an uncaught error, with `reportError`: the console shows it, and the page's `error` listeners hear it, so an app's error list (groundwork's `:groundwork/browser-error`) gets it as it got the unhandled rejection before; where the browser has no `reportError` (node), the console's error;
- it is never sent again: `:retry-writes` is for `:groundwork-steps/no-answer`, an answer that didn't come; a bug sent again fails again;
- it asks nothing again and isn't a signed-out answer, as any refusal.

**Why:** `:groundwork-steps/failed` is groundwork-steps's reason for a bug, and the page needs only to know the write didn't go through; the details are the browser's error report, as the server's are its log. A failed answer is the shape every write's event already handles.

**Instead of:**
- **passing the failure on, as before:** the event never runs; the form hangs
- **`:groundwork-steps/no-answer`:** it would be sent again, and a page would say "no connection" for a bug
- **a new reason of groundwork-screens's own:** every app's catalogs and pages would need to learn it, for what `:groundwork-steps/failed` says
- **the start's `:on-answer-failed` for it:** that is the app's one hook for questions; the write's own event is where its form is
