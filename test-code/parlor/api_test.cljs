(ns parlor.api-test
  "parlor on node, over doorbell's real question store asking through a
   stand-in send-message! that answers at once, notes the tables each
   message read, and fails a name on demand. mailroom's routing, which
   needs a browser's history, is replaced where a test follows pages."
  (:require
   [cljs.test :refer [async deftest is testing]]
   [doorbell.api :as doorbell]
   [mailroom.api :as mailroom]
   [parlor.api :as parlor]
   [parlor.can-i :as can-i]
   [re-frame.core :as rf]
   [re-frame.db :as rf-db]))

(def ^:private store-settings {:gather-changes-ms 10 :keep-unwatched-answers-ms 1000})

(defn- make-stand-in-server
  "A send-message! counting asks by name and answering (answer-of name n),
   noting the tables tables-by-name says the name reads; a name in the
   failing atom is answered {:error \"Not found.\"}, one in the offline
   atom gets no answer."
  [tables-by-name answer-of]
  (let [asked   (atom {})
        failing (atom #{})
        offline (atom #{})]
    {:asked         asked
     :failing       failing
     :offline       offline
     :send-message! (fn [message]
                      (let [[message-name] (first message)
                            n              (get (swap! asked update message-name (fnil inc 0)) message-name)]
                        (if (contains? @offline message-name)
                          (js/Promise.reject (js/Error. "offline"))
                          (js/Promise.resolve
                           (cond-> {message-name (if (contains? @failing message-name)
                                                   {:error "Not found."}
                                                   (answer-of message-name n))}
                             (contains? tables-by-name message-name)
                             (assoc :mailroom/notes {message-name {:tables (get tables-by-name message-name)}}))))))}))

(defn- after-ms [ms f] (js/setTimeout f ms))

(defn- sub
  "A subscription's value now, read fresh outside a screen."
  [query]
  (rf/clear-subscription-cache!)
  @(rf/subscribe query))

;; the tests read subscriptions outside a screen on purpose: re-frame's
;; warning about it is left out, every other warning stays
(rf/set-loggers! {:warn (fn [& args]
                          (when-not (some #(and (string? %) (.includes % "outside of a reactive context")) args)
                            (apply js/console.warn args)))})

(def ^:private heard (atom []))

(rf/reg-event-db :test/heard (fn [db [_ & more]] (swap! heard conj (vec more)) db))

(defn- start!
  "A fresh db and a started parlor, following no address."
  [send-message! more-settings]
  (reset! rf-db/app-db {})
  (reset! heard [])
  (let [store (doorbell/make-question-store send-message! store-settings)]
    (with-redefs [mailroom/start-following-address! (fn [_ _] nil)]
      (rf/dispatch-sync [:parlor/start (merge {:store             store
                                               :send-message!     send-message!
                                               :pages             {}
                                               :questions-of-pages {}
                                               :actions-question  nil
                                               :on-answer-failed  nil
                                               :on-move           nil}
                                              more-settings)]))
    store))

(defn- thrown-data [f]
  (try (f) nil (catch :default e (ex-data e))))

(deftest the-settings-are-required
  (reset! rf-db/app-db {})
  (let [{:keys [type errors]} (thrown-data #(rf/dispatch-sync [:parlor/start {:pages {} :colour "red"}]))]
    (is (= :parlor/bad-settings type))
    (is (= #{:store :send-message! :questions-of-pages :actions-question :on-answer-failed :on-move :colour}
           (set (keys errors)))
        "every missing key and the unknown one, at once"))
  (let [{:keys [type errors]} (thrown-data #(rf/dispatch-sync [:parlor/start {:store              {}
                                                                              :send-message!      identity
                                                                              :pages              {:orders/page {:address "/orders" :title nil}}
                                                                              :questions-of-pages {:shops/page (fn [_] [])}
                                                                              :actions-question   nil
                                                                              :on-answer-failed   nil
                                                                              :on-move            nil}]))]
    (is (= :parlor/bad-settings type))
    (is (= {:questions-of-pages ["no questions declared for the page :orders/page" "no page :shops/page in :pages"]} errors)
        "every page declares its questions, and only pages there are")))

(deftest nothing-asks-before-the-start
  (reset! rf-db/app-db {})
  (doseq [[event names] [[[:parlor/watch :orders-page [[:orders/all {}] [:shops/all {}]]] [:orders/all :shops/all]]
                         [[:parlor/own :orders-page [[:orders/all {}]]] [:orders/all]]
                         [[:parlor/ask-again [:orders/all {}]] [:orders/all]]
                         [[:parlor/once-answered [:orders/all {}] [:test/heard]] [:orders/all]]
                         [[:parlor/send [:orders/move {:order/id "1"}] [:test/heard]] [:orders/move]]]]
    (let [{:keys [type message-names]} (thrown-data #(rf/dispatch-sync event))]
      (is (= :parlor/asked-before-start type) (str (first event) " throws"))
      (is (= names message-names) "naming the messages")))
  (testing "release asks nothing, so it doesn't throw"
    (is (nil? (thrown-data #(rf/dispatch-sync [:parlor/release :orders-page]))))))

(deftest the-four-statuses
  (async done
         (let [{:keys [asked failing send-message!]} (make-stand-in-server {:orders/all ["orders"]} (fn [_ n] [n]))
               _ (start! send-message! {:on-answer-failed [:test/heard :failed]})
               q [:orders/all {}]]
           (rf/dispatch-sync [:parlor/watch :orders-page [q]])
           (is (= {:status :loading} (sub [:parlor/answer q])) "loading until the first answer")
           (after-ms 20
                     (fn []
                       (is (= {:status :ready :data [1]} (sub [:parlor/answer q])))
                       (swap! failing conj :orders/all)
                       (rf/dispatch-sync [:parlor/ask-again q])
                       (after-ms 20
                                 (fn []
                                   (is (= {:status :refresh-error :data [1] :error {:error "Not found."}}
                                          (sub [:parlor/answer q]))
                                       "a failed refresh keeps the good data")
                                   (is (= [[:failed q {:error "Not found."}]] @heard) "the app hears of it")
                                   (swap! failing disj :orders/all)
                                   (rf/dispatch-sync [:parlor/ask-again q])
                                   (after-ms 20
                                             (fn []
                                               (is (= {:status :ready :data [3]} (sub [:parlor/answer q])))
                                               (is (= 3 (:orders/all @asked)))
                                               (done))))))))))

(deftest a-failed-first-answer-is-an-error-and-asking-again-loads
  (async done
         (let [{:keys [failing send-message!]} (make-stand-in-server {} (fn [_ n] n))
               _ (start! send-message! {})
               q [:orders/one {:order/id "7"}]]
           (swap! failing conj :orders/one)
           (rf/dispatch-sync [:parlor/watch :order-panel [q]])
           (after-ms 20
                     (fn []
                       (is (= {:status :error :error {:error "Not found."}} (sub [:parlor/answer q])))
                       (swap! failing disj :orders/one)
                       (rf/dispatch-sync [:parlor/ask-again q])
                       (is (= {:status :loading} (sub [:parlor/answer q])) "loading, not the old error")
                       (after-ms 20
                                 (fn []
                                   (is (= {:status :ready :data 2} (sub [:parlor/answer q])))
                                   (done))))))))

(deftest own-and-release
  (async done
         (let [{:keys [asked send-message!]} (make-stand-in-server {} (fn [_ n] n))
               store (start! send-message! {})
               a     [:orders/all {}]
               b     [:shops/all {}]
               c     [:people/all {}]]
           (rf/dispatch-sync [:parlor/watch :orders-page [a b]])
           (rf/dispatch-sync [:parlor/watch :side-panel [b]])
           (after-ms 20
                     (fn []
                       (is (= {:orders/all 1 :shops/all 1} @asked) "a question held twice is watched once")
                       (rf/dispatch-sync [:parlor/own :orders-page [b c]])
                       (after-ms 20
                                 (fn []
                                   (is (= {:orders/all 1 :shops/all 1 :people/all 1} @asked)
                                       "what it keeps isn't asked again; the new one is")
                                   (is (= {:status :loading} (sub [:parlor/answer a])) "the one let go is dropped")
                                   (is (= 1 (doorbell/find-kept-answer store a)) "doorbell keeps its answer a while")
                                   (rf/dispatch-sync [:parlor/release :orders-page])
                                   (is (= {:status :ready :data 1} (sub [:parlor/answer b])) "the side panel still holds it")
                                   (is (= {:status :loading} (sub [:parlor/answer c])))
                                   (rf/dispatch-sync [:parlor/watch :orders-page [a]])
                                   (after-ms 20
                                             (fn []
                                               (is (= {:status :ready :data 2} (sub [:parlor/answer a]))
                                                   "held again: doorbell's kept answer, then the fresh one")
                                               (done))))))))))

(deftest once-answered
  (async done
         (let [{:keys [send-message!]} (make-stand-in-server {} (fn [_ n] n))
               _ (start! send-message! {})
               q [:orders/one {:order/id "7"}]]
           (rf/dispatch-sync [:parlor/once-answered q [:test/heard :nothing-holds-it]])
           (rf/dispatch-sync [:parlor/watch :order-form [q]])
           (rf/dispatch-sync [:parlor/once-answered q [:test/heard :waited]])
           (after-ms 20
                     (fn []
                       (rf/dispatch-sync [:parlor/once-answered q [:test/heard :now]])
                       (after-ms 10
                                 (fn []
                                   (is (= [[:waited 1] [:now 1]] @heard)
                                       "told once with the answer, or at once when it is there; nothing for a question nothing holds")
                                   (done))))))))

(deftest a-write-asks-again-what-it-touched
  (async done
         (let [{:keys [asked failing offline send-message!]}
               (make-stand-in-server {:orders/all ["orders"] :shops/all ["shops"] :orders/move ["orders" "order_moves"]}
                                     (fn [message-name n] (if (= :orders/move message-name) {:moved n} n)))
               _ (start! send-message! {})]
           (rf/dispatch-sync [:parlor/watch :orders-page [[:orders/all {}] [:shops/all {}]]])
           (after-ms 20
                     (fn []
                       (rf/dispatch-sync [:parlor/send [:orders/move {:order/id "7"}] [:test/heard :moved]])
                       (after-ms 40
                                 (fn []
                                   (is (= [[:moved {:status :ready :data {:moved 1}}]] @heard))
                                   (is (= {:orders/all 2 :shops/all 1 :orders/move 1} @asked)
                                       "the write's tables are asked again; others aren't")
                                   (is (= {:status :ready :data 2} (sub [:parlor/answer [:orders/all {}]])))
                                   (reset! heard [])
                                   (swap! failing conj :orders/move)
                                   (rf/dispatch-sync [:parlor/send [:orders/move {:order/id "7"}] [:test/heard :moved]])
                                   (after-ms 40
                                             (fn []
                                               (is (= [[:moved {:status :error :error {:error "Not found."}}]] @heard))
                                               (is (= 2 (:orders/all @asked)) "a failed write asks nothing again")
                                               (reset! heard [])
                                               (reset! offline #{:orders/move})
                                               (rf/dispatch-sync [:parlor/send [:orders/move {:order/id "7"}] nil])
                                               (rf/dispatch-sync [:parlor/send [:orders/move {:order/id "7"}] [:test/heard :moved]])
                                               (after-ms 40
                                                         (fn []
                                                           (is (= [[:moved {:status :error :error {:error "No answer from the server."}}]] @heard)
                                                               "no answer is an error; nil means no event")
                                                           (done))))))))))))

(deftest a-write-without-notes-asks-again-everything-watched
  (async done
         (let [{:keys [asked send-message!]} (make-stand-in-server {:orders/all ["orders"] :shops/all ["shops"]}
                                                                   (fn [_ n] n))
               _ (start! send-message! {})]
           (rf/dispatch-sync [:parlor/watch :orders-page [[:orders/all {}] [:shops/all {}]]])
           (after-ms 20
                     (fn []
                       (rf/dispatch-sync [:parlor/send [:orders/import {}] nil])
                       (after-ms 30
                                 (fn []
                                   (is (= {:orders/all 2 :shops/all 2 :orders/import 1} @asked))
                                   (done))))))))

(deftest can-i
  (async done
         (let [shop  #uuid "5b000000-0000-4000-8000-000000000001"
               other #uuid "5b000000-0000-4000-8000-000000000002"
               {:keys [send-message!]}
               (make-stand-in-server {} (fn [_ _] [{:scope-kind :shop :scope-id shop :actions [:orders/see :orders/edit]}
                                                   {:scope-kind :shop :scope-id other :actions [:orders/see]}
                                                   {:scope-kind nil :scope-id nil :actions [:profile/edit]}]))
               _ (start! send-message! {:actions-question [:people/actions {}]})]
           (is (false? (sub [:parlor/can-i? :orders/see [:shop shop]])) "no until the answer is there")
           (after-ms 20
                     (fn []
                       (is (true? (sub [:parlor/can-i? :orders/edit [:shop shop]])))
                       (is (true? (sub [:parlor/can-i? :orders/edit [:shop (str shop)]])) "an id and its text are one")
                       (is (false? (sub [:parlor/can-i? :orders/edit [:shop other]])))
                       (is (false? (sub [:parlor/can-i? :orders/edit [:workshop shop]])) "the kind counts")
                       (is (true? (sub [:parlor/can-i? :orders/see [[:shop shop] [:shop other]]])) "in every one")
                       (is (false? (sub [:parlor/can-i? :orders/edit [[:shop shop] [:shop other]]])))
                       (is (false? (sub [:parlor/can-i? :orders/see []])) "an empty list is no")
                       (is (true? (sub [:parlor/can-i? :profile/edit [:shop other]])) "nil kind and id: every scope")
                       (rf/dispatch-sync [:parlor/release :orders-page])
                       (is (true? (sub [:parlor/can-i? :orders/see [:shop shop]])) "the start's owner keeps it")
                       (is (false? (can-i/can-i? nil :orders/see [:shop shop])) "no answer, no")
                       (done))))))

(deftest pages-are-re-frame-data
  (async done
         (let [{:keys [asked send-message!]} (make-stand-in-server {} (fn [_ n] n))
               store     (doorbell/make-question-store send-message! store-settings)
               on-page   (atom nil)
               orders    {:name :orders/page :page {:address "/orders" :title "Orders" :name :orders/page}
                          :path-params {} :query {}}
               order     {:name :orders/order :page {:address "/orders/:order-id" :title "Order" :name :orders/order}
                          :path-params {:order-id "7"} :query {}}]
           (reset! rf-db/app-db {})
           (reset! heard [])
           (with-redefs [mailroom/start-following-address! (fn [pages f]
                                                             (is (= #{:orders/page :orders/order} (set (keys pages))))
                                                             (reset! on-page f))]
             (rf/dispatch-sync [:parlor/start {:store             store
                                               :send-message!     send-message!
                                               :pages             {:orders/page  {:address "/orders" :title "Orders"}
                                                                   :orders/order {:address "/orders/:order-id" :title "Order"}}
                                               :questions-of-pages {:orders/page  (fn [_] [[:orders/all {}] [:shops/all {}]])
                                                                    :orders/order (fn [{:keys [path-params]}]
                                                                                    [[:orders/one {:order/id (:order-id path-params)}]
                                                                                     [:shops/all {}]])}
                                               :actions-question  nil
                                               :on-answer-failed  nil
                                               :on-move           [:test/heard :moved]}]))
           (@on-page orders)
           (after-ms 20
                     (fn []
                       (is (= orders (sub [:parlor/page])))
                       (is (= {:orders/all 1 :shops/all 1} @asked) "the page's questions, asked on arrival")
                       (@on-page order)
                       (after-ms 20
                                 (fn []
                                   (is (= {:orders/all 1 :shops/all 1 :orders/one 1} @asked)
                                       "what the next page shares is kept, not asked again")
                                   (is (= {:status :loading} (sub [:parlor/answer [:orders/all {}]])) "the rest let go")
                                   (@on-page nil)
                                   (after-ms 20
                                             (fn []
                                               (is (nil? (sub [:parlor/page])) "an address no page has")
                                               (is (= {:status :loading} (sub [:parlor/answer [:shops/all {}]])))
                                               (is (= [[:moved {:from nil :to orders}]
                                                       [:moved {:from orders :to order}]
                                                       [:moved {:from order :to nil}]]
                                                      @heard))
                                               (let [moved (atom nil)]
                                                 (with-redefs [mailroom/go-to-page! (fn [& args] (reset! moved (vec args)))]
                                                   (rf/dispatch-sync [:parlor/go-to :orders/order {:order-id "8"}]))
                                                 (is (= [:orders/order {:order-id "8"}] @moved)))
                                               (done))))))))))
