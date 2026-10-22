# "Can I?" checks the answer by the shape it is handed; parlor's copy of doorman's shape is gone

> The user's decision, 2026-10-23. Since v0.6.0. Changes in part ["Can I?" reads an answer the app names](2026-10-22-can-i-reads-an-answer-the-app-names.md) and [parlor uses its libraries' own functions](2026-10-23-parlor-uses-its-libraries-own-functions.md): `actions-answer-shape` is no longer parlor's.

**Decision:** doorman owns the shape of "what may I do?"'s answer (doorman v0.6.0's `doorman.shapes`, in the browser through `doorman.api`). parlor no longer has `actions-answer-shape`. The start takes two more settings, every key required:

- `:actions-answer-shape`: the shape the `:actions-question`'s answer fits, doorman's `my-actions-answer-shape` in house; nil exactly when `:actions-question` is nil.
- `:wire`: the app's wire, which the answer is checked by (wire's `find-errors-in-data-for-shape`; in the browser an id is its text, which plain malli would refuse).

`[:parlor/can-i? action scope]` reads the answer only when it fits the shape; one that doesn't is no, as one still loading or failed.

**Why:** the shape is written where the answer is made; the app hands it in, as it hands every message's shapes to mailroom. Checked by the shape, a server answering something else (an older or newer doorman) says no instead of reading the wrong keys.

**The price:** two more start settings; the check runs each time the actions answer changes.

**Instead of:**
- parlor keeping its copy, with a test in house that the two match: two copies, one test between them
- trusting mailroom's check alone: parlor would read whatever the app named, with no shape of its own to say what it reads
