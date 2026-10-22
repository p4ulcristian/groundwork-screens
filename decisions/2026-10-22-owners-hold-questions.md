# Owners hold questions; a page owns its questions

> Carries over Groundwork v0.5.0's `::store/ensure`, `::store/own` and `::store/release`, and a route's `:reads`.

**Decision:**

- **An owner** (any value: a page, a panel, a form) **holds questions**: `[:parlor/watch owner questions]` adds them, `[:parlor/own owner questions]` makes them exactly these (what it held and still wants stays as it is, unasked; the rest are let go; the new ones are asked), `[:parlor/release owner]` lets go of all. A question is `[name params]`, doorbell's.
- **A question is watched in doorbell once**, however many owners hold it, from the first owner to the last; showing data never asks by itself.
- **The page owns its questions**: on every move, the owner `:parlor/page` owns exactly `(questions-of-page page)`, the start's required setting; an address no page has owns none. What the next page shares is kept, not asked again.
- **The start's `:actions-question`** is held by the owner `:parlor/start` for as long as the app runs.
- **`[:parlor/ask-again question]`** asks a held question again now (doorbell's `ask-question-again!`): a refresh button, a try-again, a timer. One nothing holds isn't asked. After a failed first answer it reads `:loading`.
- **`[:parlor/once-answered question event]`** tells `event` the data once: now when good data is there (`:ready` or `:refresh-error`), else with the next good answer; a failed answer, or the question let go, tells it nothing. A question nothing holds tells it nothing.

**Why:** a screen says what it shows when it opens and lets go when it closes; one owner per page makes a move one call, with what two pages share kept.

**The price:** `once-answered` can't wait for an answer already out when good data is there: doorbell doesn't say whether a question is out, so v0.5.0's "waits for the request in flight" is now "tells the data there". A page's questions are a function of the page, not data on the page: the pages come from the server as JSON, which carries no functions.

**Instead of:**
- one doorbell watch per owner: the same question watched twice, asked twice
- `:reads` on the page record: the page record is the server's JSON (mailroom's pages)
