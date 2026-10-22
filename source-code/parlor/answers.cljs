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
     :send-write!, with a one-time key made for this send (a random id):
     a send that gets no answer is sent again with the same key, the
     start's :retry-writes, and the server saves it once. Its event gets
     {:status :ready :data answer} or {:status :error :error answer}. A
     good answer asks again, through doorbell, every watched question that
     read a table the write's notes name, as a change heard on the sse line
     does; doorbell's ring for the same change comes too.
   - Nothing asks before :parlor/start: watch, own, ask-again,
     once-answered and send throw, naming the messages."
  (:require
   [clojure.string :as str]
   [doorbell.api :as doorbell]
   [mailroom.api :as mailroom]
   [re-frame.core :as rf]))

(def no-answer
  "What a message that got no answer (the network, the server down) is
   answered: doorbell's words for the same."
  {:error "No answer from the server."})

(defn failed?
  "Whether an answer is a failure rather than data: mailroom's error
   answers (mailroom's is-error-answer?: {:error …} or {:errors …} and
   nothing else, or an answer carrying :mailroom/errors), doorbell's
   no-answer among them."
  [answer]
  (mailroom/is-error-answer? answer))

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
  (let [tables (get-in answer [:mailroom/notes message-name :tables])]
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
  "A write's one-time key: a random id, text."
  []
  (str (random-uuid)))

(defn send-with-retries
  "(send-write! message once-key), and again with the same key while it
   gets no answer (its promise fails), up to times more, wait-ms apart. A
   promise of the first answer, or failed after the last try."
  [send-write! message once-key {:keys [times wait-ms] :as retry-writes}]
  (-> (send-write! message once-key)
      (.catch (fn [error]
                (if (pos? times)
                  (-> (js/Promise. (fn [resolve _] (js/setTimeout resolve wait-ms)))
                      (.then #(send-with-retries send-write! message once-key (assoc retry-writes :times (dec times)))))
                  (js/Promise.reject error))))))

(rf/reg-fx :parlor/-send
           (fn [{:keys [started message-name params on-answer]}]
             (-> (send-with-retries (:send-write! started) {message-name params} (make-once-key) (:retry-writes started))
                 (.then (fn [answer] answer) (fn [_] {message-name no-answer}))
                 (.then (fn [answer]
                          (let [data (get answer message-name)]
                            (when-not (failed? data)
                              (ask-again-what-changed! started answer message-name))
                            (when on-answer
                              (rf/dispatch (conj on-answer (if (failed? data)
                                                             {:status :error :error data}
                                                             {:status :ready :data data}))))))))))

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
                   (let [parlor        (parlor-of db)
                         held?         (contains? (:answers parlor) question)
                         [parlor tell] (take-answer parlor question answer)
                         failed-event  (get-in parlor [:started :on-answer-failed])]
                     {:db (assoc db :parlor parlor)
                      :fx (cond-> (mapv (fn [event] [:dispatch event]) tell)
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
                 (fn [{:keys [db]} [_ [message-name params] on-answer]]
                   (started! db "send" [[message-name params]])
                   {:parlor/-send {:started      (get-in db [:parlor :started])
                                   :message-name message-name
                                   :params       params
                                   :on-answer    on-answer}}))

;; -----------------------------------------------------------------------------
;; Subscriptions

(rf/reg-sub :parlor/-answers (fn [db _] (get-in db [:parlor :answers])))

(rf/reg-sub :parlor/answer
            :<- [:parlor/-answers]
            (fn [answers [_ question]]
              (dissoc (get answers question {:status :loading}) :owners)))
