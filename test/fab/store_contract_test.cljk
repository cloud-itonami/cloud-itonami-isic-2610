(ns fab.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [fab.robotics :as robotics]
            [fab.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Fab Lot 4" (:lot-name (store/lot s "lot-1"))))
      (is (= "JPN" (:jurisdiction (store/lot s "lot-1"))))
      (is (= 90 (:good-dies (store/lot s "lot-1"))))
      (is (= 100 (:total-dies (store/lot s "lot-1"))))
      (is (= 0.85 (:required-yield-share (store/lot s "lot-1"))))
      (is (false? (:process-defect-flag-unresolved? (store/lot s "lot-1"))))
      (is (= 70 (:good-dies (store/lot s "lot-3"))))
      (is (true? (:process-defect-flag-unresolved? (store/lot s "lot-4"))))
      (is (false? (:robotics-sim-verified? (store/lot s "lot-1"))) "no robotics mission has run yet")
      (is (true? (:robotics-sim-verified? (store/lot s "lot-5"))) "seeded as already-on-file")
      (is (= 25.0 (:bond-wire-diameter-um (store/lot s "lot-1"))))
      (is (number? (:bond-pull-strength-actual (store/lot s "lot-1"))) "real fab.simphysics telemetry on file")
      (is (not (robotics/bond-pull-strength-out-of-range? (store/lot s "lot-1")))
          "lot-1's real standard-wire-diameter pull-test simulation clears the real tolerance band")
      (is (= 15.0 (:bond-wire-diameter-um (store/lot s "lot-5")))
          "lot-5 is deliberately recorded with a much thinner wire -- see fab.store/demo-data")
      (is (< (:bond-pull-strength-actual (store/lot s "lot-5")) (:bond-pull-strength-min (store/lot s "lot-5")))
          "lot-5's real simulated pull force genuinely falls below the real tolerance band")
      (is (false? (:process-step-dispatched? (store/lot s "lot-1"))))
      (is (false? (:yield-audit-finalized? (store/lot s "lot-1"))))
      (is (= ["lot-1" "lot-2" "lot-3" "lot-4" "lot-5"]
             (mapv :id (store/all-lots s))))
      (is (nil? (store/defect-screen-of s "lot-1")))
      (is (nil? (store/verification-of s "lot-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/audit-history s)))
      (is (zero? (store/next-dispatch-sequence s "JPN")))
      (is (zero? (store/next-audit-sequence s "JPN")))
      (is (false? (store/lot-already-dispatched? s "lot-1")))
      (is (false? (store/lot-already-audited? s "lot-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :lot/upsert
                                 :value {:id "lot-1" :lot-name "Sakura Fab Lot 4"}})
        (is (= "Sakura Fab Lot 4" (:lot-name (store/lot s "lot-1"))))
        (is (= 90 (:good-dies (store/lot s "lot-1"))) "unrelated field preserved"))
      (testing "robotics-sim result commits via :lot/upsert and reads back"
        (store/commit-record! s {:effect :lot/upsert
                                 :value {:id "lot-1" :robotics-sim-verified? true
                                        :robotics-sim-record {:mission-id "m-1" :passed? true}}})
        (is (true? (:robotics-sim-verified? (store/lot s "lot-1"))))
        (is (= {:mission-id "m-1" :passed? true} (:robotics-sim-record (store/lot s "lot-1"))))
        (is (= 90 (:good-dies (store/lot s "lot-1"))) "unrelated field still preserved"))
      (testing "verification / defect-screen payloads commit and read back"
        (store/commit-record! s {:effect :verification/set :path ["lot-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/verification-of s "lot-1")))
        (store/commit-record! s {:effect :defect-screen/set :path ["lot-1"]
                                 :payload {:lot-id "lot-1" :verdict :resolved}})
        (is (= {:lot-id "lot-1" :verdict :resolved} (store/defect-screen-of s "lot-1"))))
      (testing "process-step dispatch drafts a record and advances the sequence"
        (store/commit-record! s {:effect :lot/mark-dispatched :path ["lot-1"]})
        (is (= "JPN-DSP-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "process-step-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:process-step-dispatched? (store/lot s "lot-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "JPN")))
        (is (true? (store/lot-already-dispatched? s "lot-1")))
        (is (false? (store/lot-already-dispatched? s "lot-2"))))
      (testing "yield audit drafts a record and advances the sequence"
        (store/commit-record! s {:effect :lot/mark-audited :path ["lot-1"]})
        (is (= "JPN-YLD-000000" (get (first (store/audit-history s)) "record_id")))
        (is (= "yield-audit-draft" (get (first (store/audit-history s)) "kind")))
        (is (true? (:yield-audit-finalized? (store/lot s "lot-1"))))
        (is (= 1 (count (store/audit-history s))))
        (is (= 1 (store/next-audit-sequence s "JPN")))
        (is (true? (store/lot-already-audited? s "lot-1")))
        (is (false? (store/lot-already-audited? s "lot-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/lot s "nope")))
    (is (= [] (store/all-lots s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/audit-history s)))
    (is (zero? (store/next-dispatch-sequence s "JPN")))
    (is (zero? (store/next-audit-sequence s "JPN")))
    (store/with-lots s {"x" {:id "x" :lot-name "n" :good-dies 90 :total-dies 100
                             :required-yield-share 0.85
                             :process-defect-flag-unresolved? false
                             :process-step-dispatched? false :yield-audit-finalized? false
                             :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:lot-name (store/lot s "x"))))))
