(ns parlor.start
  "The start: :parlor/start takes the settings (parlor.settings), checks
   them, keeps them in the db under :parlor :started with the store and the
   send function the app made, follows the address bar, and has the owner
   :parlor/start watch the :actions-question. A second start lets go of
   everything the first one watched."
  (:require
   [parlor.answers :as answers]
   [parlor.settings :as settings]
   [re-frame.core :as rf]))

(rf/reg-event-fx :parlor/start
                 (fn [{:keys [db]} [_ start-settings]]
                   (let [started            (assoc (settings/check-start-settings start-settings) :stops (atom {}))
                         before             (get-in db [:parlor :started])
                         parlor             {:started started :answers {} :waiting {} :page nil}
                         {:keys [actions-question]} started
                         [parlor to-watch]  (if actions-question
                                              (answers/add-owner parlor :parlor/start [actions-question])
                                              [parlor []])]
                     {:db (assoc db :parlor parlor)
                      :fx (cond-> []
                            before         (conj [:parlor/-unwatch-all before])
                            (seq to-watch) (conj [:parlor/-watch {:started started :questions to-watch}])
                            true           (conj [:parlor/-follow-address (:pages started)]))})))
