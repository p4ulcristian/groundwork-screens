# Every start setting is required; parlor takes no log

> Follows mailroom's [Every value that sets behaviour is a setting](https://github.com/p4ulcristian/mailroom/blob/main/decisions/2026-10-22-every-value-that-sets-behaviour-is-a-setting.md).

**Decision:** `[:parlor/start settings]` checks its settings with malli first, `start-settings-shape`, the map closed, every key required, nil written out where nothing applies; a missing or unknown key refuses, `:parlor/bad-settings`, with every problem at once:

```clojure
{:store             store             ; doorbell's question store
 :send-message!     send-message!     ; (fn [message]) -> a promise of the answer, the one the store asks through
 :pages             pages             ; the server's, from the page
 :questions-of-page questions-of-page ; (fn [page]) -> questions, [] for none
 :actions-question  nil               ; or the question "can I?" reads
 :on-answer-failed  nil               ; or an event
 :on-move           nil}              ; or an event
```

A second start lets go of everything the first one watched and starts empty.

**parlor takes no log.** It writes nothing: a failed answer goes to the screens (its status) and to the app's `:on-answer-failed`; a programming mistake (bad settings, an ask before the start) throws. When it has something to write, it takes the app's `:log`, `(log level event data)`, as the server's libraries do.

**What stays fixed, and why:** the owner names `:parlor/page` and `:parlor/start` (the protocol the app reads); "No answer from the server." (doorbell's words, which parlor's screens show alike); the db key `:parlor`.

**Why:** a value left to a default is a value the app doesn't show.

**Instead of:** defaults for the events and the actions question (nil written out says "chosen", not "forgotten"); a `:log` that nothing is written to.
