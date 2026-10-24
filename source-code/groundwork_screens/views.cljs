(ns groundwork-screens.views
  "Everything of groundwork-screens that is Reagent's, in one namespace:
   the logic namespaces (answers, pages, can-i, start, texts, settings,
   routes) require no Reagent, only re-frame.

   - The root screen drawn into an element of the page (mount-root!), on
     React's root for that element, made once and kept on the element, so
     drawing again (after a save in development) reuses it. The first
     drawing waits for the first catalogs (groundwork-screens.texts): until
     they are in, the element keeps what the page put in it (a splash), and
     no screen draws a text as nothing.
   - The page shown, or the placeholder while its module loads
     (show-page).
   - tr, the one text call, which only a view may make: it subscribes.
   - The scroll a move asks for, run once Reagent has drawn the page: the
     effect :groundwork-screens/-scroll, which a page's arrival
     (groundwork-screens.pages) returns."
  (:require
   [groundwork-screens.pages :as pages]
   [groundwork-screens.routes :as routes]
   [groundwork-screens.texts :as texts]
   [re-frame.core :as rf]
   [re-frame.db :as rf-db]
   [reagent.core :as reagent]
   [reagent.dom.client :as reagent-client]
   [reagent.ratom :as ratom]))

;; -----------------------------------------------------------------------------
;; The scroll after a move, once drawn

(defn scroll-after-drawing!
  "Scrolls as the routes told, once Reagent has drawn the page."
  [scroll]
  (reagent/after-render #(routes/scroll-as-told! scroll)))

(rf/reg-fx :groundwork-screens/-scroll (fn [scroll] (scroll-after-drawing! scroll)))

;; -----------------------------------------------------------------------------
;; The root screen

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

;; -----------------------------------------------------------------------------
;; The page shown

(defn show-page
  "A component: [draw-page page], the page shown (nil for an address no
   page has); while the page's module loads, [placeholder page status]
   instead, the start's :placeholder, status :loading, or :failed when it
   couldn't load."
  [draw-page]
  (let [page        @(rf/subscribe [:groundwork-screens/page])
        module-name (pages/find-module-of-page page)
        status      @(rf/subscribe [:groundwork-screens/-module-status module-name])
        placeholder @(rf/subscribe [:groundwork-screens/-placeholder])]
    (if (and module-name (not= :loaded status))
      [placeholder page status]
      [draw-page page])))

;; -----------------------------------------------------------------------------
;; Texts

(defn tr
  "The key's message in the page's language, args filled in, the one text
   call in the browser; only while a view draws (Reagent's reactive
   context), else it throws :groundwork-screens/tr-outside-a-view, naming
   the key."
  [message-key args]
  (when-not (ratom/reactive?)
    (throw (ex-info (str "(screens/tr " message-key ") outside a view: an event stores the key and its args, and the view that shows it calls tr")
                    {:type :groundwork-screens/tr-outside-a-view :key message-key})))
  @(rf/subscribe [:groundwork-screens/-text message-key args]))
