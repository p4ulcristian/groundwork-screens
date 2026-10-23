# A write carries a one-time key, kept across its retries

> **Changed in part** by [A refusal is a named reason, declared on its handler](https://github.com/p4ulcristian/bridge/blob/tidy-up/decisions/2026-10-23-a-refusal-is-a-named-reason-declared-on-its-handler.md) (bridge's, 2026-10-23, built on tidy-up, not released): a write is sent again only when answered `:bridge/no-answer`; bridge's `send-message!` no longer fails for want of an answer, and a failed promise is passed on.

> **Changed in part** by [The family and its composer are groundwork](https://github.com/p4ulcristian/groundwork/blob/Starting-from-the-bottom/decisions/2026-10-23-the-family-and-its-composer-are-groundwork.md) (2026-10-23): manor, the family, and house, the composer, are both named groundwork now.

> The user's decision, 2026-10-23 (house's "run-once keys for every write"). Since v0.4.0. Changes in part [What parlor leaves out of Groundwork v0.5.0](2026-10-22-what-parlor-leaves-out.md) (the one-time `:key` is back) and [Every start setting is required](2026-10-22-every-start-setting-is-required.md): `:send-message!` is replaced by `:send-write!`, and `:retry-writes` is new. Carries over Groundwork v0.5.0's `::send-write` (`(assoc ask :key (or (:key ask) (str (random-uuid))))`).

**Decision:**

- **Every `[:parlor/send [name params] event]` makes a one-time key**, a random id, and sends the write through the start's `:send-write!`, `(fn [message once-key])`. parlor doesn't say how the key travels; house puts it in the `idempotency-key` header, and its server saves a repeat of the same key once and answers it the first answer.
- **A send that gets no answer is sent again with the same key**: the start's `:retry-writes`, `{:times 2 :wait-ms 1000}`, up to `:times` more, `:wait-ms` apart; `{:times 0 …}` for never. No answer is a promise that failed: the network, the server down, a proxy's error page. A write the server answered, even with an error, is never sent again. After the last try the event gets `{:status :error :error {:error "No answer from the server."}}`, as before.
- **The start's settings**: `:send-message!` is gone (writes were its only use in parlor; the store keeps its own); `:send-write!` and `:retry-writes` come in, every key required.

```clojure
(rf/dispatch-sync [:parlor/start {:store              store
                                  :send-write!        (fn [message once-key] …)   ; house's: the key in the idempotency-key header
                                  :retry-writes       {:times 2 :wait-ms 1000}
                                  …}])
```

**Why:** a write that got no answer may have been saved or not; sending it again with a new key could save it twice (an order placed twice), and not sending it leaves the person to guess. With the same key, the server tells the two apart: saved, it answers what it answered; not saved, it saves now. Retrying is where the key earns its place: without a retry, every send has its own key and no repeat ever matches. Only "no answer" is retried: an answer, even a refusal, is the server's word, and sending it again would only repeat it.

**The price:** a write the server is slow to answer can be sent again while the first still runs (a proxy timing out); the server holds the repeat until the first is done (house's decision). A person who clicks twice sends two writes, two keys: the key guards against the network, not the button; a screen disables its button while its write is on its way.

**Instead of:**
- the app passing its own key with each send: every screen would make keys, and a forgotten one would save twice
- retrying in mailroom's `send-message!`: reads don't need it (doorbell asks again after a break), and mailroom doesn't know which messages write
- retrying every failed answer: a refusal sent again is refused again
