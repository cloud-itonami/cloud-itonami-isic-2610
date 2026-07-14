(ns fab.governor-contract-test
  "The governor contract as executable tests -- the fab-operator
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    Fab Advisor never dispatches a process step or finalizes a yield
    audit the Fab Operations Governor would reject, `:actuation/
    dispatch-process-step`/`:actuation/finalize-yield-audit` NEVER
    auto-commit at any phase, `:lot/intake` (no direct capital risk)
    MAY auto-commit when clean, and every decision (commit OR hold)
    leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [fab.store :as store]
            [fab.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :fab-engineer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a requirements
  verification on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :requirements/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through process-defect screening -> approve,
  leaving a screening on file. Only safe to call for a lot whose
  defect status has already resolved -- an unresolved flag HARD-holds
  the screen itself (see
  `process-defect-flag-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :defect/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(defn- simulate-robotics!
  "Walks `subject` through the robot wafer-probe/optical-inspection/
  wire-bond-pull-test verification mission -> approve, leaving
  `:robotics-sim-verified?` on file. This now ACTUALLY runs the real
  `fab.simphysics`-backed pull-test simulation for the lot's own
  `:bond-wire-diameter-um` (ADR-2607152000) -- only meaningful to call
  for a lot whose real simulated pull-test force is actually within
  tolerance -- an out-of-tolerance lot still gets :robotics-sim-
  verified? recorded (per whatever the mission itself found), but
  `fab.governor`'s independent recheck HARD-holds regardless (see
  `robotics-simulation-out-of-tolerance-is-held`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-robotics") {:op :robotics/simulate-process-step :subject subject} operator)
  (approve! actor (str tid-prefix "-robotics")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :lot/intake :subject "lot-1"
                   :patch {:id "lot-1" :lot-name "Sakura Fab Lot 4"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Fab Lot 4" (:lot-name (store/lot db "lot-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest requirements-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :requirements/verify :subject "lot-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/verification-of db "lot-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a requirements/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :requirements/verify :subject "lot-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/verification-of db "lot-1")) "no verification written"))))

(deftest dispatch-process-step-without-verification-is-held
  (testing "actuation/dispatch-process-step before any requirements verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest yield-rate-insufficient-is-held
  (testing "a lot whose own yield rate falls below its own required threshold -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "lot-3")
          res (exec-op actor "t5" {:op :actuation/finalize-yield-audit :subject "lot-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:yield-rate-insufficient} (-> (store/ledger db) last :basis)))
      (is (empty? (store/audit-history db))))))

(deftest process-defect-flag-is-held-and-unoverridable
  (testing "an unresolved process-defect flag on a lot -> HOLD, and never reaches request-approval -- exercised via :defect/screen DIRECTLY, not via the actuation op against an unscreened lot (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, aerospace's, recovery's, consulting's, union's and congregation's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :defect/screen :subject "lot-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:process-defect-flag-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/defect-screen-of db "lot-4")) "no clearance written"))))

(deftest dispatch-process-step-always-escalates-then-human-decides
  (testing "a clean, fully-verified lot still ALWAYS interrupts for human approval -- actuation/dispatch-process-step is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "lot-1")
          _ (simulate-robotics! actor "t7pre2" "lot-1")
          r1 (exec-op actor "t7" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:process-step-dispatched? (store/lot db "lot-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest finalize-yield-audit-always-escalates-then-human-decides
  (testing "a clean, fully-verified, sufficient-yield lot still ALWAYS interrupts for human approval -- actuation/finalize-yield-audit is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "lot-1")
          _ (screen! actor "t8pre2" "lot-1")
          r1 (exec-op actor "t8" {:op :actuation/finalize-yield-audit :subject "lot-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, audit record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:yield-audit-finalized? (store/lot db "lot-1"))))
          (is (= 1 (count (store/audit-history db))) "one draft audit record"))))))

(deftest dispatch-process-step-double-dispatch-is-held
  (testing "dispatching the same lot's process step twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "lot-1")
          _ (simulate-robotics! actor "t9pre2" "lot-1")
          _ (exec-op actor "t9a" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest finalize-yield-audit-double-finalization-is-held
  (testing "finalizing the same lot's yield audit twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "lot-1")
          _ (screen! actor "t10pre2" "lot-1")
          _ (exec-op actor "t10a" {:op :actuation/finalize-yield-audit :subject "lot-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/finalize-yield-audit :subject "lot-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-audited} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/audit-history db))) "still only the one earlier finalization"))))

(deftest robotics-simulation-always-needs-approval
  (testing "robotics/simulate-process-step is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t11" {:op :robotics/simulate-process-step :subject "lot-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t11")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:robotics-sim-verified? (store/lot db "lot-1"))))))))

(deftest dispatch-process-step-without-robotics-simulation-is-held
  (testing "actuation/dispatch-process-step before the robot wafer-probe/wire-bond mission ever ran -> HOLD (robotics-simulation-missing)"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "lot-1")
          res (exec-op actor "t12" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:robotics-simulation-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest robotics-simulation-out-of-tolerance-is-held
  (testing "lot-5 has a robotics-sim already on file, but its own REAL fab.simphysics-simulated bond-pull-strength reading (ADR-2607152000) falls outside the real tolerance band on INDEPENDENT recheck -> HOLD, never trusts the on-file verdict alone. lot-5 is deliberately recorded with a much thinner bond wire (:bond-wire-diameter-um 15.0 vs. the ~25um standard the other lots use) in the demo fixture (fab.store/demo-data) -- a genuine process-record inconsistency the real, re-run simulation catches."
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "lot-5")
          res (exec-op actor "t13" {:op :actuation/dispatch-process-step :subject "lot-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:robotics-simulation-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :lot/intake :subject "lot-1"
                          :patch {:id "lot-1" :lot-name "Sakura Fab Lot 4"}} operator)
      (exec-op actor "b" {:op :requirements/verify :subject "lot-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
