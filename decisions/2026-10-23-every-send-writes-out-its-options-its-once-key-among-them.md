# Every send writes out its options, its once-key among them

> The user's decision, 2026-10-23, from Iron Rainbow's port (GROUNDWORK-GAPS.md, G7; G12 fits the same map). Not built yet.

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
