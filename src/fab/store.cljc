(ns fab.store
  "SSoT for the fab actor, behind a `Store` protocol so the backend is
  a swap, not a rewrite -- the same seam every prior `cloud-itonami-
  isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/fab/store_contract_test.clj), which is the whole point: the
  actor, the Fab Operations Governor and the audit ledger never know
  which SSoT they run on.

  Like every prior dual-actuation sibling, this actor has TWO
  actuation events (dispatching a process step, finalizing a yield
  audit) acting on the SAME entity (a lot), each with its OWN history
  collection, sequence counter and dedicated double-actuation-guard
  boolean (`:process-step-dispatched?`/`:yield-audit-finalized?`,
  never a `:status` value) -- the same discipline every prior sibling
  governor's guards establish, informed by `cloud-itonami-isic-6492`'s
  status-lifecycle bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which lot was
  screened for an unresolved process-defect flag, which process step
  was dispatched, which yield audit was finalized, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a fab operator needs, and the
  evidence needed if a dispatch or yield-audit decision is later
  disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [fab.registry :as registry]
            [fab.robotics :as robotics]
            [langchain.db :as d]))

(defprotocol Store
  (lot [s id])
  (all-lots [s])
  (defect-screen-of [s lot-id] "committed process-defect-flag screening verdict for a lot, or nil")
  (verification-of [s lot-id] "committed requirements verification, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only process-step-dispatch history (fab.registry drafts)")
  (audit-history [s] "the append-only yield-audit history (fab.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-audit-sequence [s jurisdiction] "next audit-number sequence for a jurisdiction")
  (lot-already-dispatched? [s lot-id] "has this lot's process step already been dispatched?")
  (lot-already-audited? [s lot-id] "has this lot's yield audit already been finalized?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-lots [s lots] "replace/seed the lot directory (map id->lot)"))

;; ----------------------------- demo data -----------------------------

(defn- with-bond-pull-telemetry
  "Merges REAL bond-pull-test telemetry onto a demo lot's base fields --
  `fab.robotics/bond-pull-telemetry-for` actually runs `fab.
  simphysics`'s `physics-2d`-stepped simulation for this lot's own
  `:bond-wire-diameter-um` (ADR-2607152000), so even the 'already on
  file' seed data (as if from an earlier real destructive pull-test
  report) is genuinely simulation-derived, never a hand-typed double."
  [base]
  (merge base (select-keys (robotics/bond-pull-telemetry-for base)
                           [:bond-pull-strength-actual])))

(defn demo-data
  "A small, self-contained lot set covering both actuation lifecycles
  (dispatching a process step, finalizing a yield audit) so the actor
  + tests run offline. `:bond-wire-diameter-um` (ADR-2607152000) is a
  permanent lot-design field (like `:good-dies`/`:total-dies`);
  `:bond-pull-strength-actual` is the REAL `fab.simphysics`-computed
  pull-test force for that field (`with-bond-pull-telemetry`), the
  ground truth `fab.robotics/simulation-out-of-tolerance?` independently
  rechecks against the UNCHANGED `:bond-pull-strength-min`/`:bond-pull-
  strength-max` = [6.0 12.0] gf band. lot-5 is DELIBERATELY recorded
  with a much thinner wire (`:bond-wire-diameter-um` 15.0, vs. the
  standard ~25 μm the other lots use) -- a genuine process-record
  inconsistency (a real gold bonding wire this thin genuinely pulls
  well below this fab's own required minimum) that the real, re-run
  simulation catches on independent recheck even though
  `:robotics-sim-verified?` was seeded `true` (\"already on file\", i.e.
  someone/something marked it passed without this real simulation ever
  having run) -- the fab analog of automotive vehicle-5's
  misclassification, which lot-1..4's genuinely-consistent standard
  wire diameters clear the real tolerance band with margin (see
  `fab.simphysics/reference-pull-force-gf`)."
  []
  {:lots
   (into {}
         (map (fn [l] [(:id l) (with-bond-pull-telemetry l)]))
         [{:id "lot-1" :lot-name "Sakura Fab Lot 4"
           :good-dies 90 :total-dies 100 :required-yield-share 0.85
           :bond-wire-diameter-um 25.0 :bond-pull-strength-min 6.0 :bond-pull-strength-max 12.0
           :process-defect-flag-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :process-step-dispatched? false :yield-audit-finalized? false
           :jurisdiction "JPN" :status :intake}
          {:id "lot-2" :lot-name "Atlantis Fab Lot"
           :good-dies 90 :total-dies 100 :required-yield-share 0.85
           :bond-wire-diameter-um 26.5 :bond-pull-strength-min 6.0 :bond-pull-strength-max 12.0
           :process-defect-flag-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :process-step-dispatched? false :yield-audit-finalized? false
           :jurisdiction "ATL" :status :intake}
          {:id "lot-3" :lot-name "鈴木ファブロット"
           :good-dies 70 :total-dies 100 :required-yield-share 0.85
           :bond-wire-diameter-um 24.0 :bond-pull-strength-min 6.0 :bond-pull-strength-max 12.0
           :process-defect-flag-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :process-step-dispatched? false :yield-audit-finalized? false
           :jurisdiction "JPN" :status :intake}
          {:id "lot-4" :lot-name "田中ファブロット"
           :good-dies 90 :total-dies 100 :required-yield-share 0.85
           :bond-wire-diameter-um 25.5 :bond-pull-strength-min 6.0 :bond-pull-strength-max 12.0
           :process-defect-flag-unresolved? true
           :robotics-sim-verified? false :robotics-sim-record nil
           :process-step-dispatched? false :yield-audit-finalized? false
           :jurisdiction "JPN" :status :intake}
          {:id "lot-5" :lot-name "佐藤ファブロット"
           :good-dies 90 :total-dies 100 :required-yield-share 0.85
           :bond-wire-diameter-um 15.0 :bond-pull-strength-min 6.0 :bond-pull-strength-max 12.0
           :process-defect-flag-unresolved? false
           :robotics-sim-verified? true :robotics-sim-record nil
           :process-step-dispatched? false :yield-audit-finalized? false
           :jurisdiction "JPN" :status :intake}])})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-process-step!
  "Backend-agnostic `:lot/mark-dispatched` -- looks up the lot via the
  protocol and drafts the process-step-dispatch record, and returns
  {:result .. :lot-patch ..} for the caller to persist."
  [s lot-id]
  (let [l (lot s lot-id)
        seq-n (next-dispatch-sequence s (:jurisdiction l))
        result (registry/register-process-step-dispatch lot-id (:jurisdiction l) seq-n)]
    {:result result
     :lot-patch {:process-step-dispatched? true
                :dispatch-number (get result "dispatch_number")}}))

(defn- finalize-yield-audit!
  "Backend-agnostic `:lot/mark-audited` -- looks up the lot via the
  protocol and drafts the yield-audit record, and returns {:result ..
  :lot-patch ..} for the caller to persist."
  [s lot-id]
  (let [l (lot s lot-id)
        seq-n (next-audit-sequence s (:jurisdiction l))
        result (registry/register-yield-audit lot-id (:jurisdiction l) seq-n)]
    {:result result
     :lot-patch {:yield-audit-finalized? true
                :audit-number (get result "audit_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (lot [_ id] (get-in @a [:lots id]))
  (all-lots [_] (sort-by :id (vals (:lots @a))))
  (defect-screen-of [_ id] (get-in @a [:defect-screens id]))
  (verification-of [_ lot-id] (get-in @a [:verifications lot-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (audit-history [_] (:audits @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-audit-sequence [_ jurisdiction] (get-in @a [:audit-sequences jurisdiction] 0))
  (lot-already-dispatched? [_ lot-id] (boolean (get-in @a [:lots lot-id :process-step-dispatched?])))
  (lot-already-audited? [_ lot-id] (boolean (get-in @a [:lots lot-id :yield-audit-finalized?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :lot/upsert
      (swap! a update-in [:lots (:id value)] merge value)

      :verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :defect-screen/set
      (swap! a assoc-in [:defect-screens (first path)] payload)

      :lot/mark-dispatched
      (let [lot-id (first path)
            {:keys [result lot-patch]} (dispatch-process-step! s lot-id)
            jurisdiction (:jurisdiction (lot s lot-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:lots lot-id] merge lot-patch)
                       (update :dispatches registry/append result))))
        result)

      :lot/mark-audited
      (let [lot-id (first path)
            {:keys [result lot-patch]} (finalize-yield-audit! s lot-id)
            jurisdiction (:jurisdiction (lot s lot-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:audit-sequences jurisdiction] (fnil inc 0))
                       (update-in [:lots lot-id] merge lot-patch)
                       (update :audits registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-lots [s lots] (when (seq lots) (swap! a assoc :lots lots)) s))

(defn seed-db
  "A MemStore seeded with the demo lot set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :defect-screens {} :ledger [] :dispatch-sequences {}
                           :dispatches [] :audit-sequences {} :audits []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/defect-screen payloads, ledger
  facts, dispatch/audit records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:lot/id                            {:db/unique :db.unique/identity}
   :verification/lot-id               {:db/unique :db.unique/identity}
   :defect-screen/lot-id              {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :dispatch/seq                      {:db/unique :db.unique/identity}
   :audit/seq                         {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :audit-sequence/jurisdiction       {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- lot->tx [{:keys [id lot-name good-dies total-dies required-yield-share
                        bond-wire-diameter-um
                        bond-pull-strength-actual bond-pull-strength-min bond-pull-strength-max
                        process-defect-flag-unresolved? robotics-sim-verified? robotics-sim-record
                        process-step-dispatched? yield-audit-finalized?
                        jurisdiction status dispatch-number audit-number]}]
  (cond-> {:lot/id id}
    lot-name                                 (assoc :lot/lot-name lot-name)
    good-dies                                (assoc :lot/good-dies good-dies)
    total-dies                               (assoc :lot/total-dies total-dies)
    required-yield-share                     (assoc :lot/required-yield-share required-yield-share)
    bond-wire-diameter-um                     (assoc :lot/bond-wire-diameter-um bond-wire-diameter-um)
    bond-pull-strength-actual                (assoc :lot/bond-pull-strength-actual bond-pull-strength-actual)
    bond-pull-strength-min                   (assoc :lot/bond-pull-strength-min bond-pull-strength-min)
    bond-pull-strength-max                   (assoc :lot/bond-pull-strength-max bond-pull-strength-max)
    (some? process-defect-flag-unresolved?)  (assoc :lot/process-defect-flag-unresolved? process-defect-flag-unresolved?)
    (some? robotics-sim-verified?)            (assoc :lot/robotics-sim-verified? robotics-sim-verified?)
    (some? robotics-sim-record)               (assoc :lot/robotics-sim-record (enc robotics-sim-record))
    (some? process-step-dispatched?)         (assoc :lot/process-step-dispatched? process-step-dispatched?)
    (some? yield-audit-finalized?)           (assoc :lot/yield-audit-finalized? yield-audit-finalized?)
    jurisdiction                              (assoc :lot/jurisdiction jurisdiction)
    status                                    (assoc :lot/status status)
    dispatch-number                           (assoc :lot/dispatch-number dispatch-number)
    audit-number                              (assoc :lot/audit-number audit-number)))

(def ^:private lot-pull
  [:lot/id :lot/lot-name :lot/good-dies :lot/total-dies :lot/required-yield-share
   :lot/bond-wire-diameter-um
   :lot/bond-pull-strength-actual :lot/bond-pull-strength-min :lot/bond-pull-strength-max
   :lot/process-defect-flag-unresolved? :lot/robotics-sim-verified? :lot/robotics-sim-record
   :lot/process-step-dispatched? :lot/yield-audit-finalized?
   :lot/jurisdiction :lot/status :lot/dispatch-number :lot/audit-number])

(defn- pull->lot [m]
  (when (:lot/id m)
    {:id (:lot/id m) :lot-name (:lot/lot-name m)
     :good-dies (:lot/good-dies m)
     :total-dies (:lot/total-dies m)
     :required-yield-share (:lot/required-yield-share m)
     :bond-wire-diameter-um (:lot/bond-wire-diameter-um m)
     :bond-pull-strength-actual (:lot/bond-pull-strength-actual m)
     :bond-pull-strength-min (:lot/bond-pull-strength-min m)
     :bond-pull-strength-max (:lot/bond-pull-strength-max m)
     :process-defect-flag-unresolved? (boolean (:lot/process-defect-flag-unresolved? m))
     :robotics-sim-verified? (boolean (:lot/robotics-sim-verified? m))
     :robotics-sim-record (dec* (:lot/robotics-sim-record m))
     :process-step-dispatched? (boolean (:lot/process-step-dispatched? m))
     :yield-audit-finalized? (boolean (:lot/yield-audit-finalized? m))
     :jurisdiction (:lot/jurisdiction m) :status (:lot/status m)
     :dispatch-number (:lot/dispatch-number m) :audit-number (:lot/audit-number m)}))

(defrecord DatomicStore [conn]
  Store
  (lot [_ id]
    (pull->lot (d/pull (d/db conn) lot-pull [:lot/id id])))
  (all-lots [_]
    (->> (d/q '[:find [?id ...] :where [?e :lot/id ?id]] (d/db conn))
         (map #(pull->lot (d/pull (d/db conn) lot-pull [:lot/id %])))
         (sort-by :id)))
  (defect-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?lid
                :where [?k :defect-screen/lot-id ?lid] [?k :defect-screen/payload ?p]]
              (d/db conn) id)))
  (verification-of [_ lot-id]
    (dec* (d/q '[:find ?p . :in $ ?lid
                :where [?a :verification/lot-id ?lid] [?a :verification/payload ?p]]
              (d/db conn) lot-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (audit-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :audit/seq ?s] [?e :audit/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-audit-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :audit-sequence/jurisdiction ?j] [?e :audit-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (lot-already-dispatched? [s lot-id]
    (boolean (:process-step-dispatched? (lot s lot-id))))
  (lot-already-audited? [s lot-id]
    (boolean (:yield-audit-finalized? (lot s lot-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :lot/upsert
      (d/transact! conn [(lot->tx value)])

      :verification/set
      (d/transact! conn [{:verification/lot-id (first path) :verification/payload (enc payload)}])

      :defect-screen/set
      (d/transact! conn [{:defect-screen/lot-id (first path) :defect-screen/payload (enc payload)}])

      :lot/mark-dispatched
      (let [lot-id (first path)
            {:keys [result lot-patch]} (dispatch-process-step! s lot-id)
            jurisdiction (:jurisdiction (lot s lot-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(lot->tx (assoc lot-patch :id lot-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :lot/mark-audited
      (let [lot-id (first path)
            {:keys [result lot-patch]} (finalize-yield-audit! s lot-id)
            jurisdiction (:jurisdiction (lot s lot-id))
            next-n (inc (next-audit-sequence s jurisdiction))]
        (d/transact! conn
                     [(lot->tx (assoc lot-patch :id lot-id))
                      {:audit-sequence/jurisdiction jurisdiction :audit-sequence/next next-n}
                      {:audit/seq (count (audit-history s)) :audit/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-lots [s lots]
    (when (seq lots) (d/transact! conn (mapv lot->tx (vals lots)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:lots ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [lots]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-lots s lots))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo lot set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
