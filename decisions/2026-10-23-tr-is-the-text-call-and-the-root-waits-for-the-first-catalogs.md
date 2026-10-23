# `(parlor/tr key args)` is the text call; the root waits for the first catalogs

> The user's decision, 2026-10-23: "we want (tr ...)", on both sides (dictionary's [Texts are `(tr key args)`, in the language bound around them](https://github.com/p4ulcristian/dictionary/blob/main/decisions/2026-10-23-texts-are-tr-under-a-bound-language.md)). Since v0.8.0. Changes in part [Texts are re-frame data, in the page's language](2026-10-23-texts-are-re-frame-data-in-the-pages-language.md): its "The price" (a text is a subscription, not a function call; a page draws its texts after one fetch).

**Decision:**

- **`(parlor/tr :orders.cart/add {:count 3})`** is a text in the page's language, as the server's `(dictionary/tr …)` is in the request's. In a screen it is `[:parlor/text key args]`, so a swapped catalog draws the screen again; outside one (an event, a helper) it reads re-frame's db as it is. Args always given, `{}` for none.
- **`mount-root!` draws the root once the first catalogs are in** (at once without `:languages`); until then the element keeps what the page put in it, a splash. Inside the root, `tr` is never nil.
- `[:parlor/text …]` and `[:parlor/texts-ready?]` stay.

**Why:** the short call, the same on both sides. Iron Rainbow's pages wait for their catalogs before drawing (`i18n/init!`), so no screen shows an empty text; the root waiting does the same in one place, where the app would otherwise check `texts-ready?` around every screen.

**Why the root waits, not the start:** the start still asks the page's questions at once, so the answers and the catalogs come at the same time; only the drawing waits.

**The price:** a fresh page draws after one fetch of its catalog (and the fallback's), as Iron Rainbow's does; a page without a splash in its element is blank for that fetch.

**Instead of:**
- `@(rf/subscribe [:parlor/text …])` in every screen: the long call
- every screen checking `texts-ready?`: the same wait written in every app
- the start waiting for the catalogs: the questions would wait too
