(ns fab.registry-test
  (:require [clojure.test :refer [deftest is]]
            [fab.registry :as r]))

;; ----------------------------- yield-rate-insufficient? -----------------------------

(deftest not-insufficient-when-yield-meets-or-exceeds-required-share
  (is (not (r/yield-rate-insufficient? {:good-dies 90 :total-dies 100 :required-yield-share 0.85})))
  (is (not (r/yield-rate-insufficient? {:good-dies 85 :total-dies 100 :required-yield-share 0.85})))
  (is (not (r/yield-rate-insufficient? {:good-dies 100 :total-dies 100 :required-yield-share 0.85}))))

(deftest insufficient-when-yield-falls-below-required-share
  (is (r/yield-rate-insufficient? {:good-dies 70 :total-dies 100 :required-yield-share 0.85}))
  (is (r/yield-rate-insufficient? {:good-dies 0 :total-dies 100 :required-yield-share 0.85})))

(deftest insufficient-is-false-on-missing-or-zero-fields
  (is (not (r/yield-rate-insufficient? {})))
  (is (not (r/yield-rate-insufficient? {:good-dies 70})))
  (is (not (r/yield-rate-insufficient? {:good-dies 0 :total-dies 0 :required-yield-share 0.85}))
      "zero total-dies avoids divide-by-zero, treated as not-computable"))

;; ----------------------------- register-process-step-dispatch -----------------------------

(deftest dispatch-is-a-draft-not-a-real-dispatch
  (let [result (r/register-process-step-dispatch "lot-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest dispatch-assigns-dispatch-number
  (let [result (r/register-process-step-dispatch "lot-1" "JPN" 7)]
    (is (= (get result "dispatch_number") "JPN-DSP-000007"))
    (is (= (get-in result ["record" "lot_id"]) "lot-1"))
    (is (= (get-in result ["record" "kind"]) "process-step-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest dispatch-validation-rules
  (is (thrown? Exception (r/register-process-step-dispatch "" "JPN" 0)))
  (is (thrown? Exception (r/register-process-step-dispatch "lot-1" "" 0)))
  (is (thrown? Exception (r/register-process-step-dispatch "lot-1" "JPN" -1))))

;; ----------------------------- register-yield-audit -----------------------------

(deftest audit-is-a-draft-not-a-real-finalization
  (let [result (r/register-yield-audit "lot-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest audit-assigns-audit-number
  (let [result (r/register-yield-audit "lot-1" "JPN" 3)]
    (is (= (get result "audit_number") "JPN-YLD-000003"))
    (is (= (get-in result ["record" "lot_id"]) "lot-1"))
    (is (= (get-in result ["record" "kind"]) "yield-audit-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest audit-validation-rules
  (is (thrown? Exception (r/register-yield-audit "" "JPN" 0)))
  (is (thrown? Exception (r/register-yield-audit "lot-1" "" 0)))
  (is (thrown? Exception (r/register-yield-audit "lot-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-process-step-dispatch "lot-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-process-step-dispatch "lot-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-DSP-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-DSP-000001" (get-in hist2 [1 "record_id"])))))
