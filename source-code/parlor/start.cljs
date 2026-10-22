(ns parlor.start
  "The start: :parlor/start takes the settings (parlor.settings), checks
   them, keeps them in the db under :parlor :started with the store and the
   send function the app made, follows the address bar, and has the owner
   :parlor/start watch the :actions-question, and fetches the first
   catalogs (parlor.texts). A second start lets go of
   everything the first one watched."
  (:require
   [parlor.answers :as answers]
   [parlor.settings :as settings]
   [parlor.texts :as texts]
   [re-frame.core :as rf]))

(rf/reg-event-fx :parlor/start
                 (fn [{:keys [db]} [_ start-settings]]
                   (let [started            (assoc (settings/check-start-settings start-settings) :stops (atom {}))
                         before             (get-in db [:parlor :started])
                         parlor             {:started started :answers {} :waiting {} :page nil}
                         {:keys [actions-question]} started
                         [parlor to-watch]  (if actions-question
                                              (answers/add-owner parlor :parlor/start [actions-question])
                                              [parlor []])
                         [parlor fetch-fx]  (texts/start-texts parlor)]
                     {:db (assoc db :parlor parlor)
                      :fx (cond-> fetch-fx
                            before         (conj [:parlor/-unwatch-all before])
                            (seq to-watch) (conj [:parlor/-watch {:started started :questions to-watch}])
                            true           (conj [:parlor/-follow-address (select-keys started [:pages :query-shapes-of-pages :languages])]))})))
