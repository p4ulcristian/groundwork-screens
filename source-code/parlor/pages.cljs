(ns parlor.pages
  "The page the address bar leads to, as re-frame data, from mailroom's
   routing. On every move the db's :parlor :page becomes mailroom's page,
   {:name :page :path-params :query}, or nil for an address no page has;
   the owner :parlor/page owns exactly the questions the new page declares
   (its :questions-of-pages; what the next page shares is kept, the rest
   let go); then the app's :on-move event gets {:from :to}. Moving is an
   event, [:parlor/go-to name path-params]."
  (:require
   [mailroom.api :as mailroom]
   [parlor.answers :as answers]
   [re-frame.core :as rf]))

(rf/reg-fx :parlor/-follow-address
           (fn [pages]
             (mailroom/start-following-address! pages #(rf/dispatch [:parlor/-arrived %]))))

(rf/reg-fx :parlor/-go-to
           (fn [[page-name path-params]]
             (mailroom/go-to-page! page-name path-params)))

(rf/reg-event-fx :parlor/-arrived
                 (fn [{:keys [db]} [_ page]]
                   (let [{:keys [questions-of-pages on-move]} (get-in db [:parlor :started])
                         from                                (get-in db [:parlor :page])
                         {:keys [fx] :as owned}              (answers/own-in-db (assoc-in db [:parlor :page] page)
                                                                                :parlor/page
                                                                                (if page (vec ((get questions-of-pages (:name page)) page)) []))]
                     (assoc owned :fx (cond-> fx
                                        on-move (conj [:dispatch (conj on-move {:from from :to page})]))))))

(rf/reg-event-fx :parlor/go-to
                 (fn [_ [_ page-name path-params]]
                   {:parlor/-go-to [page-name path-params]}))

(rf/reg-sub :parlor/page (fn [db _] (get-in db [:parlor :page])))
