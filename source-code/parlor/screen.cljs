(ns parlor.screen
  "The root screen drawn into an element of the page with Reagent, on
   React's root for that element, made once and kept on the element, so
   drawing again (after a save in development) reuses it. The first
   drawing waits for the first catalogs (parlor.texts): until they are in,
   the element keeps what the page put in it (a splash), and no screen
   draws a text as nothing."
  (:require
   [parlor.texts :as texts]
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
   draws it anew. Called after :parlor/start. An id the page has no
   element for throws."
  [element-id root]
  (let [element (js/document.getElementById element-id)]
    (when-not element
      (throw (ex-info (str "The page has no element with the id " element-id)
                      {:type :parlor/no-element :element-id element-id})))
    (let [react-root (or (unchecked-get element "parlorRoot")
                         (let [made (reagent-client/create-root element)]
                           (unchecked-set element "parlorRoot" made)
                           made))
          watch-key  [::first-drawing element-id]]
      (remove-watch rf-db/app-db watch-key)
      (if (texts/are-texts-ready? (:parlor @rf-db/app-db))
        (draw! react-root root)
        (add-watch rf-db/app-db watch-key
                   (fn [_ _ _ db]
                     (when (texts/are-texts-ready? (:parlor db))
                       (remove-watch rf-db/app-db watch-key)
                       (draw! react-root root))))))))
