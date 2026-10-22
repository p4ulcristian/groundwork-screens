# Nothing asks before the start

> Carries over Groundwork's "Nothing asks before the start" (2026-10-13).

**Decision:** `:parlor/watch`, `:parlor/own`, `:parlor/ask-again`, `:parlor/once-answered` and `:parlor/send` before `:parlor/start` throw `:parlor/asked-before-start`, naming the messages (`:message-names`), in development and in production. `:parlor/release` asks nothing and doesn't throw; the subscriptions read `{:status :loading}`, no, and nil.

**Why:** the start hands parlor the store and the send function; a question from before it has nowhere to go and its screen waits forever. Such code asks on every page load, so a refusal shows the first time its author opens the page. What a page needs on arrival is its `:questions-of-page`.

**Instead of:** forgetting the question silently; holding early questions until the start (it works, and nobody learns the code asks at the wrong moment).
