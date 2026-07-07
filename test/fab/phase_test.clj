(ns fab.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/dispatch-process-step`/`:actuation/
  finalize-yield-audit` must NEVER be a member of any phase's `:auto`
  set."
  (:require [clojure.test :refer [deftest is testing]]
            [fab.phase :as phase]))

(deftest dispatch-process-step-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real process-step dispatch"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/dispatch-process-step))
          (str "phase " n " must not auto-commit :actuation/dispatch-process-step")))))

(deftest finalize-yield-audit-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real yield-audit finalization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/finalize-yield-audit))
          (str "phase " n " must not auto-commit :actuation/finalize-yield-audit")))))

(deftest defect-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :defect/screen))
          (str "phase " n " must not auto-commit :defect/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":lot/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:lot/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :lot/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/dispatch-process-step} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/finalize-yield-audit} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :lot/intake} :commit)))))
