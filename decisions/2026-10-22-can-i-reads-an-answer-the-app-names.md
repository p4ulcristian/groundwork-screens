# "Can I?" reads an answer the app names

> Carries over Groundwork v0.5.0's `::store/can?`, which read the answer to `:groundwork/grants`.

**Decision:** `@(rf/subscribe [:parlor/can-i? action scope])` says whether the person may do `action` in `scope`, `[kind id]` (doorman's scopes, `[:shop id]`); given a list of scopes, whether in every one (an empty list is no). It reads the answer to the start's `:actions-question`, a question the owner `:parlor/start` holds, shaped `actions-answer-shape`:

```clojure
[{:scope-kind :shop :scope-id #uuid "…" :actions [:orders/see :orders/edit]}
 {:scope-kind nil   :scope-id nil      :actions [:profile/edit]}]   ; nil kind and id: in every scope
```

- While the answer loads, after it failed, and when `:actions-question` is nil, the answer is no. After a failed refresh the last good answer counts.
- An id and its text are one id (`(str id)`), so a UUID from the wire and one from the address agree.
- It is a hint for the screen (hide a button), never access: the server's check and gates decide.
- doorbell asks it again when a table it was read from rings (doorman's tables, when the app has them tell): a new role or membership shows without a reload.

**Why:** what the person may do is an answer like any other, so it comes from the same store, with the same statuses and the same asking again. Naming the question keeps parlor out of who answers it: doorman knows the actions, house names the message.

**The price:** today no server message answers it: doorman's `list-actions-of-person` gives `{[:shop id] #{:orders/see …}}` on the server, but no handler sends it to the browser, and it leaves out the actions open to everyone (`:anyone`, `:signed-in`, `[:me …]` sentences), which v0.5.0's grants gave with no scope. Until house (or doorman) has that message, an app writes `:actions-question nil` and every "can I?" is no.

**Instead of:**
- the actions in the page shell: stale after a role changes, and a page that is cached shares them
- v0.5.0's scope id alone: doorman names a scope by its kind and id
- yes while loading: a button that shows, then vanishes
