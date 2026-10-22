# Texts are re-frame data, in the page's language; a swapped catalog draws again

> The user's decision, 2026-10-23 (dictionary's texts, carried over from Iron Rainbow's i18n): "the browser re-renders when the catalog swaps, through a re-frame subscription or a reagent atom; parlor is the re-frame layer; decide". Decided: a re-frame subscription, parlor's. Since v0.7.0. Takes dictionary v0.2.0.

**Decision:**

- **`@(rf/subscribe [:parlor/text :orders.cart/add {:count 3}])`** is the message in the page's language (dictionary's `make-text`: the language's catalog, else the fallback language's, else the key's name); `{}` for no arguments. `[:parlor/texts-ready?]` says whether the first catalogs are in.
- **The catalogs are fetched by parlor**, with dictionary's `fetch-catalog!` and the start's `:languages` (dictionary's settings): at the start the page's language's and the fallback language's; on a move to a page in another language, that language's, once.
- **While a language's catalog is on its way, the texts stay in the language shown before**; before any is in, a text is nil (nothing drawn). A catalog that couldn't be fetched counts as in and empty: its messages come from the fallback, or show as keys.
- **The catalogs live in re-frame's db** under `:parlor`; when one arrives, every screen that reads a text draws again, as for any answer.
- **Without `:languages`**, a text is its key's name and `[:parlor/texts-ready?]` is true.

**Why a subscription, not a reagent atom:** parlor already holds what screens draw from (answers, the page, "can I?") in re-frame's db, and a text depends on the page (its language): one place, one way to read it, and an event can read texts too. dictionary stays plain functions without React. Keeping the old language while the new catalog comes means a language switch never blanks the screen.

**The price:** a text is a subscription, not a function call: code outside a screen (an event) reads the db. A page opened fresh draws its texts after one fetch (`texts-ready?` lets the app wait).

**Instead of:**
- a reagent atom in dictionary (Iron Rainbow's `i18n.core/state`): dictionary would need Reagent, and the texts would live outside the db every other thing on screen comes from
- the start waiting for the catalogs: every start would wait on the network, and nothing could draw a placeholder meanwhile
