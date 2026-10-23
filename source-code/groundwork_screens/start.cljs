(ns groundwork-screens.start
  "The start: :groundwork-screens/start takes the settings (groundwork-screens.settings), checks
   them, keeps them in the db under :groundwork-screens :started with the store and the
   send function the app made, follows the address bar, and has the owner
   :groundwork-screens/start watch the :actions-question, and fetches the first
   catalogs (groundwork-screens.texts). A second start lets go of
   everything the first one watched."
  (:require
   [groundwork-screens.answers :as answers]
   [groundwork-screens.settings :as settings]
   [groundwork-screens.texts :as texts]
   [re-frame.core :as rf]))

(rf/reg-event-fx :groundwork-screens/start
                 (fn [{:keys [db]} [_ start-settings]]
                   (let [started            (assoc (settings/check-start-settings start-settings) :stops (atom {}))
                         before             (get-in db [:groundwork-screens :started])
                         screens             {:started started :answers {} :waiting {} :page nil}
                         {:keys [actions-question]} started
                         [screens to-watch]  (if actions-question
                                              (answers/add-owner screens :groundwork-screens/start [actions-question])
                                              [screens []])
                         [screens fetch-fx]  (texts/start-texts screens)]
                     {:db (assoc db :groundwork-screens screens)
                      :fx (cond-> fetch-fx
                            before         (conj [:groundwork-screens/-unwatch-all before])
                            (seq to-watch) (conj [:groundwork-screens/-watch {:started started :questions to-watch}])
                            true           (conj [:groundwork-screens/-follow-address (select-keys started [:pages :query-shapes-of-pages :languages])]))})))
