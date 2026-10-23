(ns parlor.answers
  "The answers a screen shows, in re-frame's db under :parlor, kept from
   doorbell's question store. A question is doorbell's, [name params].

   - An owner (a page, a panel) says what it shows: :parlor/watch adds
     questions, :parlor/own makes them exactly these (the ones it keeps
     stay as they are, the rest are let go), :parlor/release lets go of all
     of them. A question is watched in doorbell while any owner holds it,
     once however many do; when the last lets go, parlor drops its entry
     and doorbell keeps the answer for its :keep-unwatched-answers-ms.
   - An entry is {:status :loading}, {:status :ready :data …},
     {:status :error :error answer} or, when a fresh answer failed while
     good data was there, {:status :refresh-error :data old :error answer}:
     the old data stays on screen.
   - :parlor/ask-again asks a held question again now; one nothing holds
     isn't asked. After a failed first answer it reads as loading.
   - :parlor/once-answered tells an event the data once: now when good
     data is there and no fresher answer is on its way, else with the next
     good answer; a failed answer, or the question let go, tells it
     nothing.
   - :parlor/send sends a message that changes data, through the start's
     :send-write!, with its options written out: {:once-key :keep-alive?},
     the one-time key made by the caller (make-once-key) and kept with its
     draft, so a second Save of the same draft is the same write; and
     whether the request finishes after the page closes. A send answered
     :bridge/no-answer (bridge's: no answer came) is sent again with the
     same key, the start's :retry-writes, and the server saves it once;
     any other refusal is the server's answer. Its event
     gets {:status :ready :data answer} or {:status :error :error answer},
     the refusal as bridge gives it, {:bridge/refused … :bridge/data …
     :bridge/request-id …}. A good answer asks again, through doorbell,
     every watched question that read a table the write's notes name, as a
     change heard on the sse line does; doorbell's ring for the same change
     comes too.
   - A question's or a write's answer refused with the start's
     :signed-out-reason dispatches its :on-signed-out once per start, with
     the page the person was on, {:page :params :query}: the first such
     answer does, however many come at once.
   - Nothing asks before :parlor/start: watch, own, ask-again,
     once-answered and send throw, naming the messages."
  (:require
   [clojure.string :as str]
   [doorbell.api :as doorbell]
   [bridge.api :as bridge]
   [parlor.settings :as settings]
   [re-frame.core :as rf]))

(defn failed?
  "Whether an answer is a refusal rather than data: bridge's
   is-error-answer?, a map carrying :bridge/refused (the server's reasons,
   and bridge's own in the browser: :bridge/no-answer, :bridge/unfit)."
  [answer]
  (bridge/is-error-answer? answer))

(defn is-no-answer?
  "Whether an answer is bridge's :bridge/no-answer: no answer came, so a
   write may be sent again."
  [answer]
  (= :bridge/no-answer (:bridge/refused answer)))

(defn is-signed-out?
  "Whether an answer is refused with the start's :signed-out-reason (nil:
   never)."
  [answer signed-out-reason]
  (and (some? signed-out-reason) (failed? answer) (= signed-out-reason (:bridge/refused answer))))

(defn find-page-to-return-to
  "The page the person is on, for :on-signed-out: {:page name :params
   path-params :query query}, {:page nil :params {} :query {}} on an
   address no page has."
  [page]
  {:page   (:name page)
   :params (or (:path-params page) {})
   :query  (or (:query page) {})})

(defn tell-signed-out
  "[parlor events]: the start's :on-signed-out with the page, the first
   time this start; nothing after."
  [parlor]
  (let [{:keys [on-signed-out]} (:started parlor)]
    (if (or (nil? on-signed-out) (:told-signed-out? parlor))
      [parlor []]
      [(assoc parlor :told-signed-out? true)
       [(conj on-signed-out (find-page-to-return-to (:page parlor)))]])))

;; -----------------------------------------------------------------------------
;; The rules, as plain functions over parlor's part of the db

(defn add-owner
  "An owner holds these questions too: [parlor questions-to-watch], the
   ones nothing held before."
  [parlor owner questions]
  (reduce (fn [[parlor to-watch] question]
            (if (contains? (:answers parlor) question)
              [(update-in parlor [:answers question :owners] conj owner) to-watch]
              [(assoc-in parlor [:answers question] {:status :loading :owners #{owner}})
               (conj to-watch question)]))
          [parlor []]
          (distinct questions)))

(defn let-go
  "An owner lets go of these questions: [parlor questions-to-unwatch], the
   ones nothing holds now; their entries, and whoever waited on them, are
   dropped."
  [parlor owner questions]
  (reduce (fn [[parlor to-unwatch] question]
            (let [owners (disj (get-in parlor [:answers question :owners]) owner)]
              (if (empty? owners)
                [(-> parlor
                     (update :answers dissoc question)
                     (update :waiting dissoc question))
                 (conj to-unwatch question)]
                [(assoc-in parlor [:answers question :owners] owners) to-unwatch])))
          [parlor []]
          (filter #(contains? (get-in parlor [:answers % :owners]) owner) (distinct questions))))

(defn held-by
  "The questions an owner holds."
  [parlor owner]
  (into [] (keep (fn [[question {:keys [owners]}]] (when (contains? owners owner) question)))
        (:answers parlor)))

(defn own
  "An owner holds exactly these questions now: [parlor to-watch
   to-unwatch]. What it held and still wants stays as it is."
  [parlor owner questions]
  (let [wanted                (set questions)
        [parlor to-unwatch]   (let-go parlor owner (remove wanted (held-by parlor owner)))
        [parlor to-watch]     (add-owner parlor owner questions)]
    [parlor to-watch to-unwatch]))

(defn release
  "An owner lets go of everything it holds: [parlor to-unwatch]."
  [parlor owner]
  (let-go parlor owner (held-by parlor owner)))

(defn take-answer
  "An answer from doorbell: [parlor events-to-tell]. A question nothing
   holds any more takes nothing. A good answer tells whoever waited for it,
   with its data; a failed one drops them."
  [parlor question answer]
  (if-let [entry (get-in parlor [:answers question])]
    (let [waiting (get-in parlor [:waiting question])
          entry   (cond
                    (not (failed? answer))  {:status :ready :data answer :owners (:owners entry)}
                    (contains? entry :data) {:status :refresh-error :data (:data entry) :error answer
                                             :owners (:owners entry)}
                    :else                   {:status :error :error answer :owners (:owners entry)})]
      [(-> parlor
           (assoc-in [:answers question] entry)
           (update :waiting dissoc question))
       (if (failed? answer) [] (mapv #(conj % answer) waiting))])
    [parlor []]))

(defn ask-again
  "Ask a held question again: [parlor ask?]. After a failed first answer
   the entry reads as loading; one with data keeps showing it."
  [parlor question]
  (let [entry (get-in parlor [:answers question])]
    (cond
      (nil? entry)               [parlor false]
      (= :error (:status entry)) [(assoc-in parlor [:answers question] {:status :loading :owners (:owners entry)}) true]
      :else                      [parlor true])))

(defn find-last-answer
  "The answer an entry was made from last: its data when ready, else the
   failure it holds; nil while loading."
  [{:keys [status data error]}]
  (case status
    :ready                  data
    (:error :refresh-error) error
    nil))

(defn once-answered
  "Something waits once for a question's data: [parlor data-now-or-nil].
   With good data there and no fresher answer coming (newer-coming?,
   from doorbell) it is told at once; while loading, or with a fresher
   answer coming, it waits for the next good answer; a question nothing
   holds, or one that failed, tells it nothing."
  [parlor question event newer-coming?]
  (let [{:keys [status] :as entry} (get-in parlor [:answers question])
        wait                       #(update-in parlor [:waiting question] (fnil conj []) event)]
    (cond
      (and (#{:ready :refresh-error} status) (not newer-coming?)) [parlor (conj event (:data entry))]
      (#{:loading :ready :refresh-error} status)                  [(wait) nil]
      :else                                                       [parlor nil])))

(defn find-changed-tables
  "The tables a write's notes say it read and wrote, or :any when it
   noted none (an answer refused before its steps ran) or \"any\"."
  [answer message-name]
  (let [tables (get-in answer [:bridge/notes message-name :tables])]
    (if (or (nil? tables) (= "any" tables)) :any tables)))

;; -----------------------------------------------------------------------------
;; Events

(defn- parlor-of [db] (get db :parlor))

(defn started!
  "Throws when :parlor/start hasn't run: nothing asks before the start.
   The start hands parlor the store and the send function; a question
   from before it would have nowhere to go."
  [db what questions]
  (when-not (get-in db [:parlor :started])
    (let [names (distinct (keep first questions))]
      (throw (ex-info (str "Asked " (if (seq names) (str/join ", " names) "parlor") " (" what
                           ") before :parlor/start. Nothing asks before the start: what a page needs"
                           " on arrival is declared in its :questions-of-pages, and code that asks while its"
                           " namespace loads belongs in an event after the start.")
                      {:type :parlor/asked-before-start :what what :message-names (vec names)})))))

(rf/reg-fx :parlor/-watch
           (fn [{:keys [started questions]}]
             (let [{:keys [store stops]} started]
               (doseq [question questions]
                 (when-not (contains? @stops question)
                   (swap! stops assoc question
                          (doorbell/watch-question! store question #(rf/dispatch [:parlor/-answered question %]))))))))

(rf/reg-fx :parlor/-unwatch
           (fn [{:keys [started questions]}]
             (let [{:keys [stops]} started]
               (doseq [question questions]
                 (when-let [stop (get @stops question)]
                   (swap! stops dissoc question)
                   (stop))))))

(rf/reg-fx :parlor/-unwatch-all
           (fn [{:keys [stops]}]
             (let [all @stops]
               (reset! stops {})
               (doseq [[_ stop] all] (stop)))))

(rf/reg-fx :parlor/-ask-again
           (fn [{:keys [started question]}]
             (doorbell/ask-question-again! (:store started) question)))

(defn- ask-again-what-changed!
  "A write's good answer: every watched question that read a table it
   names is asked again, as a change heard on the sse line is."
  [{:keys [store]} answer message-name]
  (let [tables (find-changed-tables answer message-name)]
    (if (= :any tables)
      (doorbell/hear-missed-changes! store)
      (doseq [table tables]
        (doorbell/hear-changed-table! store table)))))

(defn make-once-key
  "A write's one-time key: a random id, text. The caller makes it when a
   draft starts and passes it on every send of that draft."
  []
  (str (random-uuid)))

(defn send-with-retries
  "(send-write! message send-options), and again with the same options,
   the same key among them, while the message's name is answered
   :bridge/no-answer, up to times more, wait-ms apart. A promise of the
   last answer. A promise send-write! fails is passed on as it is:
   bridge's send-message! fails none for want of an answer, so it is a
   bug, not a refusal."
  [send-write! message send-options {:keys [times wait-ms] :as retry-writes}]
  (let [message-name (key (first message))]
    (-> (send-write! message send-options)
        (.then (fn [answer]
                 (if (and (pos? times) (is-no-answer? (get answer message-name)))
                   (-> (js/Promise. (fn [resolve _] (js/setTimeout resolve wait-ms)))
                       (.then #(send-with-retries send-write! message send-options (assoc retry-writes :times (dec times)))))
                   answer))))))

(rf/reg-fx :parlor/-send
           (fn [{:keys [started message-name params on-answer send-options]}]
             (-> (send-with-retries (:send-write! started) {message-name params} send-options (:retry-writes started))
                 (.then (fn [answer]
                          (let [data (get answer message-name)]
                            (when-not (failed? data)
                              (ask-again-what-changed! started answer message-name))
                            (when (is-signed-out? data (:signed-out-reason started))
                              (rf/dispatch [:parlor/-signed-out]))
                            (when on-answer
                              (rf/dispatch (conj on-answer (if (failed? data)
                                                             {:status :error :error data}
                                                             {:status :ready :data data}))))))))))

(rf/reg-event-fx :parlor/-signed-out
                 (fn [{:keys [db]} _]
                   (let [[parlor events] (tell-signed-out (parlor-of db))]
                     {:db (assoc db :parlor parlor)
                      :fx (mapv (fn [event] [:dispatch event]) events)})))

(defn- watching-effects [started to-watch to-unwatch]
  (cond-> []
    (seq to-unwatch) (conj [:parlor/-unwatch {:started started :questions to-unwatch}])
    (seq to-watch)   (conj [:parlor/-watch {:started started :questions to-watch}])))

(defn own-in-db
  "The db and effects of an owner holding exactly these questions; the
   page's arrival uses it too."
  [db owner questions]
  (let [[parlor to-watch to-unwatch] (own (parlor-of db) owner questions)]
    {:db (assoc db :parlor parlor)
     :fx (watching-effects (:started parlor) to-watch to-unwatch)}))

(rf/reg-event-fx :parlor/watch
                 (fn [{:keys [db]} [_ owner questions]]
                   (started! db "watch" questions)
                   (let [[parlor to-watch] (add-owner (parlor-of db) owner questions)]
                     {:db (assoc db :parlor parlor)
                      :fx (watching-effects (:started parlor) to-watch [])})))

(rf/reg-event-fx :parlor/own
                 (fn [{:keys [db]} [_ owner questions]]
                   (started! db "own" questions)
                   (own-in-db db owner questions)))

(rf/reg-event-fx :parlor/release
                 (fn [{:keys [db]} [_ owner]]
                   (let [[parlor to-unwatch] (release (parlor-of db) owner)]
                     {:db (assoc db :parlor parlor)
                      :fx (watching-effects (:started parlor) [] to-unwatch)})))

(rf/reg-event-fx :parlor/-answered
                 (fn [{:keys [db]} [_ question answer]]
                   (let [parlor          (parlor-of db)
                         held?           (contains? (:answers parlor) question)
                         [parlor tell]   (take-answer parlor question answer)
                         failed-event    (get-in parlor [:started :on-answer-failed])
                         [parlor signed] (if (and held? (is-signed-out? answer (get-in parlor [:started :signed-out-reason])))
                                           (tell-signed-out parlor)
                                           [parlor []])]
                     {:db (assoc db :parlor parlor)
                      :fx (cond-> (mapv (fn [event] [:dispatch event]) (into tell signed))
                            (and held? (failed? answer) failed-event)
                            (conj [:dispatch (conj failed-event question answer)]))})))

(rf/reg-event-fx :parlor/ask-again
                 (fn [{:keys [db]} [_ question]]
                   (started! db "ask-again" [question])
                   (let [[parlor ask?] (ask-again (parlor-of db) question)]
                     (cond-> {:db (assoc db :parlor parlor)}
                       ask? (assoc :parlor/-ask-again {:started (:started parlor) :question question})))))

(rf/reg-cofx :parlor/-newer-coming
             (fn [{:keys [db] :as coeffects} _]
               (assoc coeffects :parlor/-newer-coming?
                      (fn [question]
                        (let [store (get-in db [:parlor :started :store])
                              entry (get-in db [:parlor :answers question])]
                          (or (doorbell/is-question-being-asked? store question)
                              ;; landed in doorbell, its :parlor/-answered still queued
                              (not (identical? (doorbell/find-kept-answer store question)
                                               (find-last-answer entry)))))))))

(rf/reg-event-fx :parlor/once-answered
                 [(rf/inject-cofx :parlor/-newer-coming)]
                 (fn [{:keys [db] newer-coming? :parlor/-newer-coming?} [_ question event]]
                   (started! db "once-answered" [question])
                   (let [[parlor now] (once-answered (parlor-of db) question event (newer-coming? question))]
                     (cond-> {:db (assoc db :parlor parlor)}
                       now (assoc :fx [[:dispatch now]])))))

(rf/reg-event-fx :parlor/send
                 (fn [{:keys [db]} [_ [message-name params] on-answer send-options]]
                   (started! db "send" [[message-name params]])
                   {:parlor/-send {:started      (get-in db [:parlor :started])
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
   :parlor/answer subscription gives, {:status :loading}, {:status :ready
   :data …}, {:status :error :error …} or {:status :refresh-error :data
   :error}; {:status :loading} for a question nothing holds, and before
   the start. Pure: it reads, asks nothing."
  [db question]
  (show-entry (get-in db [:parlor :answers]) question))

(rf/reg-sub :parlor/-answers (fn [db _] (get-in db [:parlor :answers])))

(rf/reg-sub :parlor/answer
            :<- [:parlor/-answers]
            (fn [answers [_ question]]
              (show-entry answers question)))
