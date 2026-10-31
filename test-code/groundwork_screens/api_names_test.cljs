(ns groundwork-screens.api-names-test
  "The public names of groundwork-screens's api, its functions and its events and
   subscriptions. Adding or removing one fails here, so the api changes on
   purpose."
  (:require
   [cljs.test :refer [deftest is]]
   [groundwork-screens.api]
   [re-frame.registrar :as registrar]))

(deftest the-function-names
  (is (= '#{start-settings-shape show-page mount-root! tr make-once-key find-answer make-address-of-page}
         (set (keys (ns-publics 'groundwork-screens.api))))))

(defn- public-screens-names [kind]
  (into #{} (filter #(and (= "groundwork-screens" (namespace %)) (not (.startsWith (name %) "-"))))
        (keys (get @registrar/kind->id->handler kind))))

(deftest the-event-and-subscription-names
  (is (= #{:groundwork-screens/start :groundwork-screens/watch :groundwork-screens/own :groundwork-screens/release :groundwork-screens/ask-again
           :groundwork-screens/once-answered :groundwork-screens/once-settled :groundwork-screens/send :groundwork-screens/go-to :groundwork-screens/hold :groundwork-screens/let-go :groundwork-screens/leave-anyway}
         (public-screens-names :event)))
  (is (= #{:groundwork-screens/answer :groundwork-screens/can-i? :groundwork-screens/page :groundwork-screens/texts-ready?}
         (public-screens-names :sub))))
