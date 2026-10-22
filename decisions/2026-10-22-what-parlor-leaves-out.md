> **Changed in part** by [A page has its query and language, scrolls once drawn, holds unsaved changes, and loads its module on the way in](2026-10-23-a-page-has-its-query-and-language-scrolls-once-drawn-holds-unsaved-changes-and-loads-its-module.md) (2026-10-23, v0.5.0): languages, scrolling, the unsaved guard, loading on demand and query filters are in.

> **Changed in part** by [A write carries a one-time key, kept across its retries](2026-10-23-a-write-carries-a-one-time-key-kept-across-its-retries.md) (2026-10-23, v0.4.0): a write's one-time key is back; the server keeps it (house).

# What parlor leaves out of Groundwork v0.5.0

**Decision:** these parts of v0.5.0's browser store are not in parlor:

| v0.5.0 | why not |
|---|---|
| `:fresh-ms` on an ask | doorbell has no freshness; a held question is watched once, and held again it shows doorbell's kept answer at once |
| `::store/hear` (an owner hears the bell for answers kept elsewhere) | v0.5.0 kept it for an app on its way to a table's page as a question of its own; that is the way now |
| `:shapes`, `:max-asks`, ids as text | the wire reads answers by their shapes (mailroom's `shapes-by-name`); asks travel one per message; an id is a UUID |
| a write's tag (its answer and the bell told apart) and its one-time `:key` | the server has neither; [a write's answer asks again what its notes name](2026-10-22-a-write-s-answer-asks-again-what-its-notes-name.md) |
| the session in the page (`::person`, `::flags`, `::language`) | the page shell has none now |
| routing's languages, scrolling, unsaved guard, loading on demand, `href`, query filters | [mailroom's routing](2026-10-22-the-page-is-re-frame-data.md) has none of them yet |
| the stylesheet loader | showroom's, with the build |

**Why:** each was v0.5.0's answer to a part that is gone or now another library's; carried over, it would be code with no caller.

**Instead of:** rebuilding them in parlor: they belong to the libraries that would have to send or know them.
