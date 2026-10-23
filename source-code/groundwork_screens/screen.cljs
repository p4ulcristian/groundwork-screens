(ns groundwork-screens.screen
  "The root screen drawn into an element of the page with Reagent, on
   React's root for that element, made once and kept on the element, so
   drawing again (after a save in development) reuses it. The first
   drawing waits for the first catalogs (groundwork-screens.texts): until they are in,
   the element keeps what the page put in it (a splash), and no screen
   draws a text as nothing."
  (:require
   [groundwork-screens.texts :as texts]
   [re-frame.core :as rf]
   [re-frame.db :as rf-db]
   [reagent.dom.client :as reagent-client]))

(defn- draw! [react-root root]
  (rf/clear-subscription-cache!)
  (reagent-client/render react-root [root]))

(defn mount-root!
  "Draws [root] into the element with this id, once the first catalogs
   are in (at once without :languages, or when they are); again with the
   same id, after a save, forgets re-frame's cached subscriptions and
   draws it anew. Before :groundwork-screens/start it throws,
   :groundwork-screens/mounted-before-start: there are no catalogs to wait for yet, and
   the root would draw with its texts as their keys' names. An id the page
   has no element for throws, :groundwork-screens/no-element."
  [element-id root]
  (when-not (get-in @rf-db/app-db [:groundwork-screens :started])
    (throw (ex-info (str "mount-root! " element-id " before :groundwork-screens/start: dispatch the start first, then mount the root")
                    {:type :groundwork-screens/mounted-before-start :element-id element-id})))
  (let [element (js/document.getElementById element-id)]
    (when-not element
      (throw (ex-info (str "The page has no element with the id " element-id)
                      {:type :groundwork-screens/no-element :element-id element-id})))
    (let [react-root (or (unchecked-get element "parlorRoot")
                         (let [made (reagent-client/create-root element)]
                           (unchecked-set element "parlorRoot" made)
                           made))
          watch-key  [::first-drawing element-id]]
      (remove-watch rf-db/app-db watch-key)
      (if (texts/are-texts-ready? (:groundwork-screens @rf-db/app-db))
        (draw! react-root root)
        (add-watch rf-db/app-db watch-key
                   (fn [_ _ _ db]
                     (when (texts/are-texts-ready? (:groundwork-screens db))
                       (remove-watch rf-db/app-db watch-key)
                       (draw! react-root root))))))))
