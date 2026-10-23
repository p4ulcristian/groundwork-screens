(ns parlor.texts
  "The texts a screen shows, in the page's language, as re-frame data:
   @(rf/subscribe [:parlor/text :orders.cart/add {:count 3}]), which
   parlor.api's tr reads. The catalogs are dictionary's, fetched from the
   server (the same files it reads): at the start the page's language's
   and the fallback language's, and on a move to a page in another
   language that one's.

   While the page's language's catalog (and the fallback's) is on its way
   the texts stay in the language shown before; before any is in, a text
   is nil and [:parlor/texts-ready?] is false (the root mount-root! draws
   waits for them, so a screen in it never reads nil). A catalog that
   couldn't be fetched counts as there and empty: its messages come from
   the fallback language, or show as their keys' names. When the catalog
   swaps, every screen reading a text draws again, as with any
   subscription. Without :languages, and before :parlor/start, a text is
   its key's name."
  (:require
   [dictionary.api :as dictionary]
   [re-frame.core :as rf]))

(rf/reg-fx :parlor/-fetch-catalogs
           (fn [{:keys [dictionary languages]}]
             (doseq [language languages]
               (-> (dictionary/fetch-catalog! dictionary language)
                   (.then #(rf/dispatch [:parlor/-catalog-arrived language %])
                          #(rf/dispatch [:parlor/-catalog-arrived language {}]))))))

(defn- has-catalog? [parlor language]
  (contains? (get-in parlor [:texts :catalogs]) language))

(defn- find-wanted-language [parlor]
  (or (get-in parlor [:page :language]) (get-in parlor [:started :languages :language-of-page])))

(defn show-texts-in-wanted-language
  "parlor with its texts shown in the page's language, once its catalog
   and the fallback's are in; else as they were."
  [parlor]
  (let [wanted (find-wanted-language parlor)]
    (if (and wanted (has-catalog? parlor wanted) (has-catalog? parlor (get-in parlor [:texts :fallback-language])))
      (assoc parlor :text-language wanted)
      parlor)))

(defn fetch-what-is-missing
  "[parlor effects]: the wanted language's and the fallback's catalogs
   fetched, unless they are in or on their way."
  [parlor]
  (let [languages (get-in parlor [:started :languages])
        missing   (when languages
                    (->> [(find-wanted-language parlor) (get-in parlor [:texts :fallback-language])]
                         (remove nil?)
                         (distinct)
                         (remove #(or (has-catalog? parlor %) (contains? (:fetching-catalogs parlor) %)))
                         (vec)))]
    (if (seq missing)
      [(update parlor :fetching-catalogs (fnil into #{}) missing)
       [[:parlor/-fetch-catalogs {:dictionary (:dictionary languages) :languages missing}]]]
      [parlor []])))

(defn start-texts
  "The texts part of a new parlor, and the effects that fetch its first
   catalogs."
  [parlor]
  (let [languages (get-in parlor [:started :languages])]
    (fetch-what-is-missing (assoc parlor
                                  :texts {:fallback-language (get-in languages [:dictionary :fallback-language]) :catalogs {}}
                                  :fetching-catalogs #{}
                                  :text-language nil))))

(rf/reg-event-db :parlor/-catalog-arrived
                 (fn [db [_ language catalog]]
                   (update db :parlor (fn [parlor]
                                        (-> parlor
                                            (assoc-in [:texts :catalogs language] catalog)
                                            (update :fetching-catalogs disj language)
                                            (show-texts-in-wanted-language))))))

(rf/reg-sub :parlor/-texts (fn [db _] (select-keys (:parlor db) [:texts :text-language :started])))

(defn find-text
  "The key's message in the language the texts are shown in: its key's
   name without :languages; nil before the first catalog is in."
  [{:keys [texts text-language started]} message-key args]
  (cond
    (nil? (:languages started)) (dictionary/make-text {:fallback-language nil :catalogs {}} nil message-key {})
    text-language               (dictionary/make-text texts text-language message-key args)
    :else                       nil))

(defn are-texts-ready?
  "Whether the first catalogs are in (always, without :languages)."
  [{:keys [text-language started]}]
  (or (nil? (:languages started)) (some? text-language)))

(rf/reg-sub :parlor/text
            :<- [:parlor/-texts]
            (fn [parlor [_ message-key args]] (find-text parlor message-key args)))

(rf/reg-sub :parlor/texts-ready?
            :<- [:parlor/-texts]
            (fn [parlor _] (are-texts-ready? parlor)))
