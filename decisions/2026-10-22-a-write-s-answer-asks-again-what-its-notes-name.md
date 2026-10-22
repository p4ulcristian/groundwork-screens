# A write's answer asks again what its notes name

> Carries over doorbell's [Asking again, and bells that miss nothing](https://github.com/p4ulcristian/doorbell/blob/main/decisions/2026-10-14-asking-again-and-bells-that-miss-nothing.md) ("a write's answer carries its own bell") onto doorbell v0.3.0.

**Decision:** `[:parlor/send [name params] on-answer]` sends a message that changes data through the start's `:send-message!`, the same function the store asks through. It is never kept.

- **Its event**, `on-answer` (nil written out for none), gets `{:status :ready :data answer}` or `{:status :error :error answer}`, the answer subscription's words; no answer at all (the network) is `{:error "No answer from the server."}`, doorbell's words.
- **A good answer asks again** every watched question that read a table the write's notes name (`:mailroom/notes`, from doorbell's `put-table-notes-into-handlers`), through doorbell's own `:doorbell/changed` handler, so it is gathered with other changes for `:gather-changes-ms`. A write's notes are the tables its queries read and wrote (Postgres's plan of an INSERT, UPDATE or DELETE names its table). Notes of `"any"`, or none (an answer refused before its steps), ask again every watched question, as doorbell's missed changes do.
- **A failed answer asks nothing again**: its transaction rolled back.
- **doorbell rings anyway**: the same commit reaches every open screen, the writer's too, over the sse line. Rung within the same gathering, the two are one round of asking; later, the writer asks once more.

**Why:** the writer sees its change as soon as its answer lands, without waiting for the sse line or depending on it (some networks hold it back), and nothing is declared: the notes are there already. Asked through doorbell's handler, a write and a ring share one gathering and doorbell's rule of which questions read what.

**The price:** the notes name every table the write read, not only the ones it changed, so a question reading a table the write only read is asked again for nothing: extra asking, never stale data. A write asks again at most twice for one change (its answer, then the ring). v0.5.0's tags, which told the two apart, are gone with the server's write tags. There is no one-time key (v0.5.0's `:key`): mailroom has no write that is answered once for a key, so a write sent again after a lost answer may save twice.

**Instead of:**
- no asking again on the answer, doorbell's ring alone: the writer waits for the ring, and without the sse line never sees its own change
- the write listing what to ask again: what doorbell's notes already say, by hand, forgotten in places
- asking again only the questions of the writer's own screen: the same tables are read by other open panels
