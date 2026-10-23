(ns parlor.api-names-test
  "The public names of parlor's api, its functions and its events and
   subscriptions. Adding or removing one fails here, so the api changes on
   purpose."
  (:require
   [cljs.test :refer [deftest is]]
   [parlor.api]
   [re-frame.registrar :as registrar]))

(deftest the-function-names
  (is (= '#{start-settings-shape show-page mount-root! tr make-once-key}
         (set (keys (ns-publics 'parlor.api))))))

(defn- public-parlor-names [kind]
  (into #{} (filter #(and (= "parlor" (namespace %)) (not (.startsWith (name %) "-"))))
        (keys (get @registrar/kind->id->handler kind))))

(deftest the-event-and-subscription-names
  (is (= #{:parlor/start :parlor/watch :parlor/own :parlor/release :parlor/ask-again
           :parlor/once-answered :parlor/send :parlor/go-to :parlor/hold :parlor/let-go}
         (public-parlor-names :event)))
  (is (= #{:parlor/answer :parlor/can-i? :parlor/page :parlor/texts-ready?}
         (public-parlor-names :sub))))
