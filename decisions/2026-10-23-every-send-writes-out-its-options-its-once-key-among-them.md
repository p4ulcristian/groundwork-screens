# Every send writes out its options, its once-key among them

> The user's decision, 2026-10-23, from Iron Rainbow's port (GROUNDWORK-GAPS.md, G7; G12 fits the same map). Built on tidy-up, not released: parlor and bridge; the composer's part comes later.

**What was wrong:** Bob presses Save, the network drops, and he presses Save again. parlor made a new once-key for every `:parlor/send`, so the second Save was a new write: a second order could be placed.

**Decision:** `:parlor/send` takes its options as a map, every key written out:

```clojure
[:parlor/send [:orders/place params] [:orders/placed]
 {:once-key    (parlor/make-once-key)   ; made by the caller; a form keeps it in its draft
  :keep-alive? false}]                  ; true: the write survives the page closing (fetch keepalive)
```

A form makes its key when its draft starts, keeps it beside the draft, and passes the same key on every Save of that draft: the server answers a repeated key with the first answer, and nothing runs twice. A new draft makes a new key.

**Why:** every send shows where its key comes from; a retry after a network error is the same write by construction, not by luck.

**The price:** every send site writes the map.

**Instead of:**
- **an optional fourth argument**: leaving it out would silently make a new key, a hidden default
- **a second event, `:parlor/send-with-key`**: two ways to send

## Settled while building

Choices made while building, not asked:

1. **`:send-write!` takes the send's options as they were written,** `(fn [message {:keys [once-key keep-alive?]}])`, in place of `(fn [message once-key])`, the same map on every retry; the app's send function puts the key in its header and hands `:keep-alive?` to bridge.
2. **bridge's `send-message!` takes a required `:keep-alive?`** (G12), fetch's `keepalive`; `false` for a plain request. A keepalive request the browser refuses (its body, with the other keepalive requests in flight, past 64 KB) fails the fetch, which bridge answers as it answers every fetch that fails: `:bridge/no-answer {:http-status nil}`, so it is retried as any write with no answer.
3. **A send without its options, or with a key missing or unknown, throws `:parlor/bad-send`,** every problem at once, before anything is sent; the once-key is text of at least one character.
4. **`make-once-key` is public in `parlor.api`,** a random UUID as text, as before; parlor no longer calls it itself.
