(ns groundwork-screens.answers
  "The answers a screen shows, in re-frame's db under :groundwork-screens, kept from
   groundwork-live-updates's question store. A question is groundwork-live-updates's, [name params].

   - An owner (a page, a panel) says what it shows: :groundwork-screens/watch adds
     questions, :groundwork-screens/own makes them exactly these (the ones it keeps
     stay as they are, the rest are let go), :groundwork-screens/release lets go of all
     of them. A question is watched in groundwork-live-updates while any owner holds it,
     once however many do; when the last lets go, groundwork-screens drops its entry
     and groundwork-live-updates keeps the answer for its :keep-unwatched-answers-ms.
   - An entry is {:status :loading}, {:status :ready :data …},
     {:status :error :error answer} or, when a fresh answer failed while
     good data was there, {:status :refresh-error :data old :error answer}:
     the old data stays on screen.
   - :groundwork-screens/ask-again asks a held question again now; one nothing holds
     isn't asked. After a failed first answer it reads as loading.
   - :groundwork-screens/once-answered tells an event the data once: now when good
     data is there and no fresher answer is on its way, else with the next
     good answer; a failed answer, or the question let go, tells it
     nothing.
   - :groundwork-screens/send sends a message that changes data, through the start's
     :send-write!, with its options written out: {:once-key :keep-alive?},
     the one-time key made by the caller (make-once-key) and kept with its
     draft, so a second Save of the same draft is the same write; and
     whether the request finishes after the page closes. A send answered
     :groundwork-steps/no-answer (groundwork-steps's: no answer came) is sent again with the
     same key, the start's :retry-writes, and the server saves it once;
     any other refusal is the server's answer. Its event
     gets {:status :ready :data answer} or {:status :error :error answer},
     the refusal as the send gives it, {:groundwork-steps/refused … :groundwork-steps/data …
     :groundwork-steps/request-id …}. A good answer asks again, through groundwork-live-updates,
     every watched question that read a table the write's notes name, as a
     change heard on the sse line does; groundwork-live-updates's ring for the same change
     comes too.
   - A question's or a write's answer refused with the start's
     :signed-out-reason dispatches its :on-signed-out once per start, with
     the page the person was on, {:page :params :query}: the first such
     answer does, however many come at once.
   - Nothing asks before :groundwork-screens/start: watch, own, ask-again,
     once-answered and send throw, naming the messages."
  (:require
   [clojure.string :as str]
   [groundwork-live-updates.api :as live-updates]
   [groundwork-steps.api :as steps]
   [groundwork-screens.settings :as settings]
   [re-frame.core :as rf]))

(defn failed?
  "Whether an answer is a refusal rather than data: groundwork-steps's
   is-error-answer?, a map carrying :groundwork-steps/refused (the server's
   reasons, and the browser's own: :groundwork-steps/no-answer,
   :groundwork-steps/unfit)."
  [answer]
  (steps/is-error-answer? answer))

(defn is-no-answer?
  "Whether an answer is groundwork-steps's :groundwork-steps/no-answer: no answer came, so a
   write may be sent again."
  [answer]
  (= :groundwork-steps/no-answer (:groundwork-steps/refused answer)))

(defn is-signed-out?
  "Whether an answer is refused with the start's :signed-out-reason (nil:
   never)."
  [answer signed-out-reason]
  (and (some? signed-out-reason) (failed? answer) (= signed-out-reason (:groundwork-steps/refused answer))))

(defn find-page-to-return-to
  "The page the person is on, for :on-signed-out: {:page name :params
   path-params :query query}, {:page nil :params {} :query {}} on an
   address no page has."
  [page]
  {:page   (:name page)
   :params (or (:path-params page) {})
   :query  (or (:query page) {})})

(defn tell-signed-out
  "[groundwork-screens events]: the start's :on-signed-out with the page, the first
   time this start; nothing after."
  [screens]
  (let [{:keys [on-signed-out]} (:started screens)]
    (if (or (nil? on-signed-out) (:told-signed-out? screens))
      [screens []]
      [(assoc screens :told-signed-out? true)
       [(conj on-signed-out (find-page-to-return-to (:page screens)))]])))

;; -----------------------------------------------------------------------------
;; The rules, as plain functions over groundwork-screens's part of the db

(defn add-owner
  "An owner holds these questions too: [groundwork-screens questions-to-watch], the
   ones nothing held before."
  [screens owner questions]
  (reduce (fn [[screens to-watch] question]
            (if (contains? (:answers screens) question)
              [(update-in screens [:answers question :owners] conj owner) to-watch]
              [(assoc-in screens [:answers question] {:status :loading :owners #{owner}})
               (conj to-watch question)]))
          [screens []]
          (distinct questions)))

(defn let-go
  "An owner lets go of these questions: [groundwork-screens questions-to-unwatch], the
   ones nothing holds now; their entries, and whoever waited on them, are
   dropped."
  [screens owner questions]
  (reduce (fn [[screens to-unwatch] question]
            (let [owners (disj (get-in screens [:answers question :owners]) owner)]
              (if (empty? owners)
                [(-> screens
                     (update :answers dissoc question)
                     (update :waiting dissoc question))
                 (conj to-unwatch question)]
                [(assoc-in screens [:answers question :owners] owners) to-unwatch])))
          [screens []]
          (filter #(contains? (get-in screens [:answers % :owners]) owner) (distinct questions))))

(defn held-by
  "The questions an owner holds."
  [screens owner]
  (into [] (keep (fn [[question {:keys [owners]}]] (when (contains? owners owner) question)))
        (:answers screens)))

(defn own
  "An owner holds exactly these questions now: [groundwork-screens to-watch
   to-unwatch]. What it held and still wants stays as it is."
  [screens owner questions]
  (let [wanted                (set questions)
        [screens to-unwatch]   (let-go screens owner (remove wanted (held-by screens owner)))
        [screens to-watch]     (add-owner screens owner questions)]
    [screens to-watch to-unwatch]))

(defn release
  "An owner lets go of everything it holds: [groundwork-screens to-unwatch]."
  [screens owner]
  (let-go screens owner (held-by screens owner)))

(defn take-answer
  "An answer from groundwork-live-updates: [groundwork-screens events-to-tell]. A question nothing
   holds any more takes nothing. A good answer tells whoever waited for it,
   with its data; a failed one drops them."
  [screens question answer]
  (if-let [entry (get-in screens [:answers question])]
    (let [waiting (get-in screens [:waiting question])
          entry   (cond
                    (not (failed? answer))  {:status :ready :data answer :owners (:owners entry)}
                    (contains? entry :data) {:status :refresh-error :data (:data entry) :error answer
                                             :owners (:owners entry)}
                    :else                   {:status :error :error answer :owners (:owners entry)})]
      [(-> screens
           (assoc-in [:answers question] entry)
           (update :waiting dissoc question))
       (if (failed? answer) [] (mapv #(conj % answer) waiting))])
    [screens []]))

(defn ask-again
  "Ask a held question again: [groundwork-screens ask?]. After a failed first answer
   the entry reads as loading; one with data keeps showing it."
  [screens question]
  (let [entry (get-in screens [:answers question])]
    (cond
      (nil? entry)               [screens false]
      (= :error (:status entry)) [(assoc-in screens [:answers question] {:status :loading :owners (:owners entry)}) true]
      :else                      [screens true])))

(defn find-last-answer
  "The answer an entry was made from last: its data when ready, else the
   failure it holds; nil while loading."
  [{:keys [status data error]}]
  (case status
    :ready                  data
    (:error :refresh-error) error
    nil))

(defn once-answered
  "Something waits once for a question's data: [groundwork-screens data-now-or-nil].
   With good data there and no fresher answer coming (newer-coming?,
   from groundwork-live-updates) it is told at once; while loading, or with a fresher
   answer coming, it waits for the next good answer; a question nothing
   holds, or one that failed, tells it nothing."
  [screens question event newer-coming?]
  (let [{:keys [status] :as entry} (get-in screens [:answers question])
        wait                       #(update-in screens [:waiting question] (fnil conj []) event)]
    (cond
      (and (#{:ready :refresh-error} status) (not newer-coming?)) [screens (conj event (:data entry))]
      (#{:loading :ready :refresh-error} status)                  [(wait) nil]
      :else                                                       [screens nil])))

(defn find-changed-tables
  "The tables a write's notes say it read and wrote, or :any when it
   noted none (an answer refused before its steps ran) or \"any\"."
  [answer message-name]
  (let [tables (get-in answer [:groundwork-steps/notes message-name :tables])]
    (if (or (nil? tables) (= "any" tables)) :any tables)))

;; -----------------------------------------------------------------------------
;; Events

(defn- screens-of [db] (get db :groundwork-screens))

(defn started!
  "Throws when :groundwork-screens/start hasn't run: nothing asks before the start.
   The start hands groundwork-screens the store and the send function; a question
   from before it would have nowhere to go."
  [db what questions]
  (when-not (get-in db [:groundwork-screens :started])
    (let [names (distinct (keep first questions))]
      (throw (ex-info (str "Asked " (if (seq names) (str/join ", " names) "parlor") " (" what
                           ") before :groundwork-screens/start. Nothing asks before the start: what a page needs"
                           " on arrival is declared in its :questions-of-pages, and code that asks while its"
                           " namespace loads belongs in an event after the start.")
                      {:type :groundwork-screens/asked-before-start :what what :message-names (vec names)})))))

(rf/reg-fx :groundwork-screens/-watch
           (fn [{:keys [started questions]}]
             (let [{:keys [store stops]} started]
               (doseq [question questions]
                 (when-not (contains? @stops question)
                   (swap! stops assoc question
                          (live-updates/watch-question! store question #(rf/dispatch [:groundwork-screens/-answered question %]))))))))

(rf/reg-fx :groundwork-screens/-unwatch
           (fn [{:keys [started questions]}]
             (let [{:keys [stops]} started]
               (doseq [question questions]
                 (when-let [stop (get @stops question)]
                   (swap! stops dissoc question)
                   (stop))))))

(rf/reg-fx :groundwork-screens/-unwatch-all
           (fn [{:keys [stops]}]
             (let [all @stops]
               (reset! stops {})
               (doseq [[_ stop] all] (stop)))))

(rf/reg-fx :groundwork-screens/-ask-again
           (fn [{:keys [started question]}]
             (live-updates/ask-question-again! (:store started) question)))

(defn- ask-again-what-changed!
  "A write's good answer: every watched question that read a table it
   names is asked again, as a change heard on the sse line is."
  [{:keys [store]} answer message-name]
  (let [tables (find-changed-tables answer message-name)]
    (if (= :any tables)
      (live-updates/hear-missed-changes! store)
      (doseq [table tables]
        (live-updates/hear-changed-table! store table)))))

(defn make-once-key
  "A write's one-time key: a random id, text. The caller makes it when a
   draft starts and passes it on every send of that draft."
  []
  (str (random-uuid)))

(defn send-with-retries
  "(send-write! message send-options), and again with the same options,
   the same key among them, while the message's name is answered
   :groundwork-steps/no-answer, up to times more, wait-ms apart. A promise of the
   last answer. A promise send-write! fails is passed on as it is:
   groundwork-bridge's send-message! fails none for want of an answer, so it is a
   bug, not a refusal."
  [send-write! message send-options {:keys [times wait-ms] :as retry-writes}]
  (let [message-name (key (first message))]
    (-> (send-write! message send-options)
        (.then (fn [answer]
                 (if (and (pos? times) (is-no-answer? (get answer message-name)))
                   (-> (js/Promise. (fn [resolve _] (js/setTimeout resolve wait-ms)))
                       (.then #(send-with-retries send-write! message send-options (assoc retry-writes :times (dec times)))))
                   answer))))))

(rf/reg-fx :groundwork-screens/-send
           (fn [{:keys [started message-name params on-answer send-options]}]
             (-> (send-with-retries (:send-write! started) {message-name params} send-options (:retry-writes started))
                 (.then (fn [answer]
                          (let [data (get answer message-name)]
                            (when-not (failed? data)
                              (ask-again-what-changed! started answer message-name))
                            (when (is-signed-out? data (:signed-out-reason started))
                              (rf/dispatch [:groundwork-screens/-signed-out]))
                            (when on-answer
                              (rf/dispatch (conj on-answer (if (failed? data)
                                                             {:status :error :error data}
                                                             {:status :ready :data data}))))))))))

(rf/reg-event-fx :groundwork-screens/-signed-out
                 (fn [{:keys [db]} _]
                   (let [[screens events] (tell-signed-out (screens-of db))]
                     {:db (assoc db :groundwork-screens screens)
                      :fx (mapv (fn [event] [:dispatch event]) events)})))

(defn- watching-effects [started to-watch to-unwatch]
  (cond-> []
    (seq to-unwatch) (conj [:groundwork-screens/-unwatch {:started started :questions to-unwatch}])
    (seq to-watch)   (conj [:groundwork-screens/-watch {:started started :questions to-watch}])))

(defn own-in-db
  "The db and effects of an owner holding exactly these questions; the
   page's arrival uses it too."
  [db owner questions]
  (let [[screens to-watch to-unwatch] (own (screens-of db) owner questions)]
    {:db (assoc db :groundwork-screens screens)
     :fx (watching-effects (:started screens) to-watch to-unwatch)}))

(rf/reg-event-fx :groundwork-screens/watch
                 (fn [{:keys [db]} [_ owner questions]]
                   (started! db "watch" questions)
                   (let [[screens to-watch] (add-owner (screens-of db) owner questions)]
                     {:db (assoc db :groundwork-screens screens)
                      :fx (watching-effects (:started screens) to-watch [])})))

(rf/reg-event-fx :groundwork-screens/own
                 (fn [{:keys [db]} [_ owner questions]]
                   (started! db "own" questions)
                   (own-in-db db owner questions)))

(rf/reg-event-fx :groundwork-screens/release
                 (fn [{:keys [db]} [_ owner]]
                   (let [[screens to-unwatch] (release (screens-of db) owner)]
                     {:db (assoc db :groundwork-screens screens)
                      :fx (watching-effects (:started screens) [] to-unwatch)})))

(rf/reg-event-fx :groundwork-screens/-answered
                 (fn [{:keys [db]} [_ question answer]]
                   (let [screens          (screens-of db)
                         held?           (contains? (:answers screens) question)
                         [screens tell]   (take-answer screens question answer)
                         failed-event    (get-in screens [:started :on-answer-failed])
                         [screens signed] (if (and held? (is-signed-out? answer (get-in screens [:started :signed-out-reason])))
                                           (tell-signed-out screens)
                                           [screens []])]
                     {:db (assoc db :groundwork-screens screens)
                      :fx (cond-> (mapv (fn [event] [:dispatch event]) (into tell signed))
                            (and held? (failed? answer) failed-event)
                            (conj [:dispatch (conj failed-event question answer)]))})))

(rf/reg-event-fx :groundwork-screens/ask-again
                 (fn [{:keys [db]} [_ question]]
                   (started! db "ask-again" [question])
                   (let [[screens ask?] (ask-again (screens-of db) question)]
                     (cond-> {:db (assoc db :groundwork-screens screens)}
                       ask? (assoc :groundwork-screens/-ask-again {:started (:started screens) :question question})))))

(rf/reg-cofx :groundwork-screens/-newer-coming
             (fn [{:keys [db] :as coeffects} _]
               (assoc coeffects :groundwork-screens/-newer-coming?
                      (fn [question]
                        (let [store (get-in db [:groundwork-screens :started :store])
                              entry (get-in db [:groundwork-screens :answers question])]
                          (or (live-updates/is-question-being-asked? store question)
                              ;; landed in groundwork-live-updates, its :groundwork-screens/-answered still queued
                              (not (identical? (live-updates/find-kept-answer store question)
                                               (find-last-answer entry)))))))))

(rf/reg-event-fx :groundwork-screens/once-answered
                 [(rf/inject-cofx :groundwork-screens/-newer-coming)]
                 (fn [{:keys [db] newer-coming? :groundwork-screens/-newer-coming?} [_ question event]]
                   (started! db "once-answered" [question])
                   (let [[screens now] (once-answered (screens-of db) question event (newer-coming? question))]
                     (cond-> {:db (assoc db :groundwork-screens screens)}
                       now (assoc :fx [[:dispatch now]])))))

(rf/reg-event-fx :groundwork-screens/send
                 (fn [{:keys [db]} [_ [message-name params] on-answer send-options]]
                   (started! db "send" [[message-name params]])
                   {:groundwork-screens/-send {:started      (get-in db [:groundwork-screens :started])
                                   :message-name message-name
                                   :params       params
                                   :on-answer    on-answer
                                   :send-options (settings/check-send-options send-options)}}))

;; -----------------------------------------------------------------------------
;; Subscriptions

(defn- show-entry
  "An entry as it is read: its status and data or error, loading for a
   question nothing holds."
  [answers question]
  (dissoc (get answers question {:status :loading}) :owners))

(defn find-answer
  "A held answer read inside an event handler, from its db: what the
   :groundwork-screens/answer subscription gives, {:status :loading}, {:status :ready
   :data …}, {:status :error :error …} or {:status :refresh-error :data
   :error}; {:status :loading} for a question nothing holds, and before
   the start. Pure: it reads, asks nothing."
  [db question]
  (show-entry (get-in db [:groundwork-screens :answers]) question))

(rf/reg-sub :groundwork-screens/-answers (fn [db _] (get-in db [:groundwork-screens :answers])))

(rf/reg-sub :groundwork-screens/answer
            :<- [:groundwork-screens/-answers]
            (fn [answers [_ question]]
              (show-entry answers question)))
