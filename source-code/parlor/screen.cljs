(ns parlor.screen
  "The root screen drawn into an element of the page with Reagent, on
   React's root for that element, made once and kept on the element, so
   drawing again (after a save in development) reuses it."
  (:require
   [re-frame.core :as rf]
   [reagent.dom.client :as reagent-client]))

(defn mount-root!
  "Draws [root] into the element with this id; again with the same id,
   after a save, forgets re-frame's cached subscriptions and draws it
   anew. An id the page has no element for throws."
  [element-id root]
  (let [element (js/document.getElementById element-id)]
    (when-not element
      (throw (ex-info (str "The page has no element with the id " element-id)
                      {:type :parlor/no-element :element-id element-id})))
    (let [react-root (or (unchecked-get element "parlorRoot")
                         (let [made (reagent-client/create-root element)]
                           (unchecked-set element "parlorRoot" made)
                           made))]
      (rf/clear-subscription-cache!)
      (reagent-client/render react-root [root]))))
