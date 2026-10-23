(ns groundwork-screens.texts
  "The texts a view shows, in the page's language, as re-frame data:
   [:groundwork-screens/-text :orders.cart/add {:count 3}], groundwork-screens's own
   subscription, which groundwork-screens.api's tr subscribes while a view draws
   (the app calls tr, never the subscription). The catalogs are groundwork-languages's, fetched from the
   server (the same files it reads): at the start the page's language's
   and the fallback language's, and on a move to a page in another
   language that one's.

   While the page's language's catalog (and the fallback's) is on its way
   the texts stay in the language shown before; before any is in, a text
   is nil and [:groundwork-screens/texts-ready?] is false (the root mount-root! draws
   waits for them, so a screen in it never reads nil). A catalog that
   couldn't be fetched counts as there and empty: its messages come from
   the fallback language, or show as their keys' names. When the catalog
   swaps, every view reading a text draws again, as with any
   subscription. Without :languages, and before :groundwork-screens/start, a text is
   its key's name."
  (:require
   [groundwork-languages.api :as languages]
   [re-frame.core :as rf]))

(rf/reg-fx :groundwork-screens/-fetch-catalogs
           (fn [{:keys [settings languages]}]
             (doseq [language languages]
               (-> (languages/fetch-catalog! settings language)
                   (.then #(rf/dispatch [:groundwork-screens/-catalog-arrived language %])
                          #(rf/dispatch [:groundwork-screens/-catalog-arrived language {}]))))))

(defn- has-catalog? [screens language]
  (contains? (get-in screens [:texts :catalogs]) language))

(defn- find-wanted-language [screens]
  (or (get-in screens [:page :language]) (get-in screens [:started :languages :language-of-page])))

(defn show-texts-in-wanted-language
  "groundwork-screens with its texts shown in the page's language, once its catalog
   and the fallback's are in; else as they were."
  [screens]
  (let [wanted (find-wanted-language screens)]
    (if (and wanted (has-catalog? screens wanted) (has-catalog? screens (get-in screens [:texts :fallback-language])))
      (assoc screens :text-language wanted)
      screens)))

(defn fetch-what-is-missing
  "[groundwork-screens effects]: the wanted language's and the fallback's catalogs
   fetched, unless they are in or on their way."
  [screens]
  (let [languages (get-in screens [:started :languages])
        missing   (when languages
                    (->> [(find-wanted-language screens) (get-in screens [:texts :fallback-language])]
                         (remove nil?)
                         (distinct)
                         (remove #(or (has-catalog? screens %) (contains? (:fetching-catalogs screens) %)))
                         (vec)))]
    (if (seq missing)
      [(update screens :fetching-catalogs (fnil into #{}) missing)
       [[:groundwork-screens/-fetch-catalogs {:settings (:settings languages) :languages missing}]]]
      [screens []])))

(defn start-texts
  "The texts part of a new groundwork-screens, and the effects that fetch its first
   catalogs."
  [screens]
  (let [languages (get-in screens [:started :languages])]
    (fetch-what-is-missing (assoc screens
                                  :texts {:fallback-language (get-in languages [:settings :fallback-language]) :catalogs {}}
                                  :fetching-catalogs #{}
                                  :text-language nil))))

(rf/reg-event-db :groundwork-screens/-catalog-arrived
                 (fn [db [_ language catalog]]
                   (update db :groundwork-screens (fn [screens]
                                        (-> screens
                                            (assoc-in [:texts :catalogs language] catalog)
                                            (update :fetching-catalogs disj language)
                                            (show-texts-in-wanted-language))))))

(rf/reg-sub :groundwork-screens/-texts (fn [db _] (select-keys (:groundwork-screens db) [:texts :text-language :started])))

(defn find-text
  "The key's message in the language the texts are shown in: its key's
   name without :languages; nil before the first catalog is in."
  [{:keys [texts text-language started]} message-key args]
  (cond
    (nil? (:languages started)) (languages/make-text {:fallback-language nil :catalogs {}} nil message-key {})
    text-language               (languages/make-text texts text-language message-key args)
    :else                       nil))

(defn find-text-or-name
  "find-text, or the key's name while no catalog is in: for a text made
   outside a view, at the moment it is shown (the browser's confirm),
   which can't wait for a catalog."
  [screens message-key args]
  (or (find-text screens message-key args)
      (languages/make-text {:fallback-language nil :catalogs {}} nil message-key {})))

(defn are-texts-ready?
  "Whether the first catalogs are in (always, without :languages)."
  [{:keys [text-language started]}]
  (or (nil? (:languages started)) (some? text-language)))

(rf/reg-sub :groundwork-screens/-text
            :<- [:groundwork-screens/-texts]
            (fn [screens [_ message-key args]] (find-text screens message-key args)))

(rf/reg-sub :groundwork-screens/texts-ready?
            :<- [:groundwork-screens/-texts]
            (fn [screens _] (are-texts-ready? screens)))
