(ns parlor.can-i
  "\"Can I?\": whether the person may do an action in a scope, read from
   the answer to the start's :actions-question (parlor.settings/
   :actions-answer-shape, doorman's): every scope they are in, [kind id], with the
   actions they may do there; an entry of nil kind and id gives its
   actions in every scope. While that answer loads, after it failed, and
   when the app asks none, and when the answer doesn't fit the start's
   :actions-answer-shape (checked by the app's wire), the answer is no. Ids are compared as text, so a
   UUID and its text are one id."
  (:require
   [re-frame.core :as rf]
   [wire.api :as wire]))

(defn- one-scope? [scope]
  (keyword? (first scope)))

(defn can-i?
  "Whether the entries give action in scope, [kind id]; given a list of
   scopes, whether in every one of them (an empty list is no)."
  [entries action scope]
  (if (one-scope? scope)
    (let [[kind id] scope]
      (boolean (some (fn [{:keys [scope-kind scope-id actions]}]
                       (and (some #{action} actions)
                            (or (and (nil? scope-kind) (nil? scope-id))
                                (and (= kind scope-kind) (= (str id) (str scope-id))))))
                     entries)))
    (boolean (and (seq scope) (every? #(can-i? entries action %) scope)))))

(rf/reg-sub :parlor/-actions
            (fn [db _]
              (let [{question :actions-question shape :actions-answer-shape the-wire :wire} (get-in db [:parlor :started])
                    {:keys [status data]} (get-in db [:parlor :answers question])]
                (when (and question
                           (#{:ready :refresh-error} status)
                           (nil? (wire/find-errors-in-data-for-shape the-wire shape data)))
                  data))))

(rf/reg-sub :parlor/can-i?
            :<- [:parlor/-actions]
            (fn [entries [_ action scope]]
              (can-i? entries action scope)))
