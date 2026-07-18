(ns fab.registry
  "Pure-function process-step-dispatch + yield-audit-finalization
  record construction -- an append-only fab-operator book-of-record
  draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a process-step-dispatch or
  yield-audit reference number -- every fab/jurisdiction assigns its
  own reference format. This namespace does NOT invent one; it builds
  a jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline `fab.
  facts` uses.

  `yield-rate-insufficient?` is the FOURTH instance of this fleet's
  ratio-based check family (`leasing.registry/collateral-coverage-
  ratio-insufficient?` established the first, MINIMUM-floor direction;
  `behavioral.registry/supervision-ratio-insufficient?` the second,
  MAXIMUM-ceiling direction; `union.registry/strike-vote-share-
  insufficient?` the third, MINIMUM-floor direction again), applying
  the SAME quotient-comparison shape -- MINIMUM-floor direction -- to
  a lot's own recorded good-dies count divided by its own recorded
  total-dies count against its own recorded required-yield-share
  threshold. This is a direct, natural mapping onto real semiconductor
  fab practice: a lot's measured yield rate must clear a required
  threshold before its yield audit can be finalized for shipment/
  certification purposes.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real fab/MES (manufacturing execution system). It
  builds the RECORD a fab operator would keep, not the act of
  dispatching the process step or finalizing the yield audit itself
  (that is `fab.operation`'s `:actuation/dispatch-process-step`/
  `:actuation/finalize-yield-audit`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  fab operator's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn yield-rate-insufficient?
  "Does `lot`'s own `:good-dies` divided by its own `:total-dies` fall
  BELOW its own recorded `:required-yield-share` threshold? A pure
  ground-truth check against the lot's own permanent fields -- no
  upstream comparison needed. The FOURTH instance of this fleet's
  ratio-based check family (see ns docstring), MINIMUM-floor direction
  like `leasing`'s and `union`'s."
  [{:keys [good-dies total-dies required-yield-share]}]
  (and (number? good-dies) (number? total-dies) (pos? total-dies)
       (number? required-yield-share)
       (< (/ (double good-dies) total-dies) required-yield-share)))

(defn register-process-step-dispatch
  "Validate + construct the PROCESS-STEP-DISPATCH registration DRAFT
  -- the fab operator's own act of dispatching a real robot process-
  step action on a lot in the cleanroom. Pure function -- does not
  touch any real fab/MES control system; it builds the RECORD an
  operator would keep. `fab.governor` independently re-verifies the
  lot's own process-defect-flag resolution status, and blocks a
  double-dispatch for the same lot, before this is ever allowed to
  commit."
  [lot-id jurisdiction sequence]
  (when-not (and lot-id (not= lot-id ""))
    (throw (ex-info "process-step-dispatch: lot_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "process-step-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "process-step-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-DSP-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "process-step-dispatch-draft"
                "lot_id" lot-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "ProcessStepDispatch" dispatch-number dispatch-number)}))

(defn register-yield-audit
  "Validate + construct the YIELD-AUDIT registration DRAFT -- the fab
  operator's own act of finalizing a real yield-audit record for a
  lot ahead of shipment/certification. Pure function -- does not touch
  any real fab/MES control system; it builds the RECORD an operator
  would keep. `fab.governor` independently re-verifies the lot's own
  yield-rate sufficiency against its own required threshold, and
  blocks a double-finalization for the same lot, before this is ever
  allowed to commit."
  [lot-id jurisdiction sequence]
  (when-not (and lot-id (not= lot-id ""))
    (throw (ex-info "yield-audit: lot_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "yield-audit: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "yield-audit: sequence must be >= 0" {})))
  (let [audit-number (str (str/upper-case jurisdiction) "-YLD-" (zero-pad sequence 6))
        record {"record_id" audit-number
                "kind" "yield-audit-draft"
                "lot_id" lot-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "audit_number" audit-number
     "certificate" (unsigned-certificate "YieldAudit" audit-number audit-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))

;; ----------------------------- :handoff (additive, optional) -----------------------------
;;
;; Part-supplier-linkage traceability (superproject part-supplier-
;; linkage ADR, cloud-itonami-isic-2610<->cloud-itonami-isic-2813):
;; `:actuation/finalize-yield-audit` may OPTIONALLY carry a `:handoff`
;; (the superproject `:handoff` shared shape, ADR-2607177600, isic-
;; 1075<->jsic-4721, REUSED AS-IS -- no new shape) naming which
;; downstream consumer (e.g. cloud-itonami-isic-2813, a pressure-
;; equipment manufacturer sourcing `part:control-panel` electronic
;; components) the finalized lot (`register-yield-audit`'s own
;; docstring already says "ahead of shipment/certification") is
;; destined for. Unlike isic-1075's own `:coordinate-shipment` (which
;; made `:handoff` MANDATORY), this actor's `:handoff` stays OPTIONAL
;; -- this fab ships lots to any customer, tracked or not, the same
;; 'optional field absent -> not checked' discipline cloud-itonami-
;; isic-2710's own `:coordinate-shipment`-`:handoff` extension
;; established. Existing callers that never set `:handoff` are
;; completely unaffected.

(defn handoff-fields-present?
  "True when `handoff` carries the three identity/correlation
  `:handoff/*` fields (`:handoff/id`/`:handoff/source-actor`/
  `:handoff/batch-id`) the superproject `:handoff` shared shape
  requires for traceability -- called ONLY when a `:handoff` map is
  actually present on a `:actuation/finalize-yield-audit` proposal
  (see `fab.governor/handoff-incomplete-violations`); a proposal with
  NO `:handoff` at all never reaches this predicate. Domain-specific
  optional fields on the shared shape (`:handoff/product-type-id`/
  `:handoff/quantity-kg`/`:handoff/cold-chain-temp-min-c`/`:handoff/
  cold-chain-temp-max-c`/`:handoff/dispatched-at-iso`) are NOT
  required here -- packaged semiconductor lots are not cold-chain
  goods, the same 'optional field absent -> not checked' discipline
  cloud-itonami-isic-2710's own handoff-compatibility check uses."
  [handoff]
  (every? some? ((juxt :handoff/id :handoff/source-actor :handoff/batch-id) handoff)))
