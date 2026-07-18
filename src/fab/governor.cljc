(ns fab.governor
  "Fab Operations Governor -- the independent compliance layer that
  earns the Fab Advisor the right to commit. The LLM has no notion of
  process-safety law, whether a lot's own measured yield rate
  actually reaches its own required threshold, whether a process-
  defect flag against the lot has actually stayed unresolved, or when
  an act stops being a draft and becomes a real-world robot process-
  step dispatch or yield-audit finalization, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD
  -- the fab-operator analog of `cloud-itonami-isic-6512`'s
  CasualtyGovernor.

  Eight checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them (you don't get to approve your way
  past a fabricated process-safety spec-basis, incomplete evidence, a
  robot wafer-probe/wire-bond verification mission that never ran or
  that independently re-checks out-of-tolerance, an unresolved
  process-defect flag, an insufficient yield rate, an upstream ore
  pedigree whose shape or claims fail independent re-verification, or
  a double dispatch/finalization). The confidence/actuation gate is
  SOFT: it
  asks a human to look (low confidence / actuation), and the human may
  approve -- but see `fab.phase`: for `:stake :actuation/dispatch-
  process-step`/`:actuation/finalize-yield-audit` (a real safety-
  critical/business-critical act) NO phase ever allows auto-commit
  either. Two independent layers agree that actuation is always a
  human call.

    1. Spec-basis                  -- did the requirements proposal cite
                                       an OFFICIAL source (`fab.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/dispatch-
                                       process-step`/`:actuation/
                                       finalize-yield-audit`, has the
                                       lot actually been verified with
                                       a full process-spec-
                                       verification-record/EDA-CAE-
                                       simulation-record/chemical-
                                       safety-clearance-record/wafer-
                                       lot-traceability-record
                                       evidence checklist on file?
    3. Robot simulation missing or
       independently out-of-
       tolerance                    -- for `:actuation/dispatch-
                                       process-step`, has the robot
                                       wafer-probe/optical-inspection/
                                       wire-bond-pull-test verification
                                       mission (`fab.robotics`)
                                       actually run and been recorded
                                       on the lot (`:robotics-sim-
                                       verified?`)? AND INDEPENDENTLY
                                       recompute whether the lot's own
                                       recorded bond-pull-strength
                                       reading -- as of ADR-2607152000,
                                       the REAL `fab.simphysics`
                                       `physics-2d`-simulated pull-test
                                       force already on file, not a
                                       hand-set value -- falls out of
                                       its own recorded tolerance
                                       bounds (`fab.robotics/
                                       simulation-out-of-tolerance?`),
                                       ignoring whatever :passed?
                                       verdict the mission run itself
                                       stored -- the same 'ground
                                       truth, not self-report'
                                       discipline check 5 below uses
                                       for yield rate.
    4. Process defect flag
       unresolved                     -- reported by THIS proposal
                                       itself (a `:defect/screen` that
                                       just found one), or already on
                                       file for the lot (`:defect/
                                       screen`/`:actuation/dispatch-
                                       process-step`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (thirty-one prior siblings,
                                       most recently `congregation.
                                       governor/safeguarding-concern-
                                       unresolved-violations`)...
                                       established -- the THIRTY-
                                       SECOND distinct application of
                                       this exact discipline. Exercised
                                       in tests/demo via `:defect/
                                       screen` DIRECTLY, not via the
                                       actuation op against an
                                       unscreened lot -- see this ns's
                                       own test suite.
    5. Yield rate insufficient      -- for `:actuation/finalize-
                                       yield-audit`, INDEPENDENTLY
                                       recompute whether the lot's own
                                       good-dies divided by its own
                                       total-dies falls below its own
                                       recorded required-yield-share
                                       threshold (`fab.registry/yield-
                                       rate-insufficient?`) -- needs no
                                       proposal inspection or stored-
                                       verdict lookup at all. The
                                       FOURTH instance of this fleet's
                                       ratio-based check family
                                       (`leasing.governor/collateral-
                                       coverage-insufficient-
                                       violations`/`behavioral.
                                       governor/supervision-ratio-
                                       insufficient-violations`/
                                       `union.governor/strike-vote-
                                       share-insufficient-violations`
                                       established the first three).
    6. Upstream ore pedigree claims
       out of tolerance              -- ADR-2607999980's smartphone-
                                       chain applied link of the
                                       ADR-2607999950 cross-actor
                                       supply-chain-linkage pattern
                                       (direct port of ADR-2607999970's
                                       `steelworks.governor` equivalent
                                       check): for `:actuation/
                                       dispatch-process-step`, when the
                                       lot carries an OPTIONAL
                                       `:upstream-ore-pedigree` (a
                                       `kotoba.pedigree` record an
                                       upstream `cloud-itonami-
                                       isic-0729` non-ferrous-ore
                                       production record issued via
                                       `nonferrousops.export/pedigree-
                                       for-production-record`),
                                       INDEPENDENTLY re-verify it --
                                       never trust the upstream
                                       actor's claim at face value:
                                       (a) `kotoba.pedigree/valid?`
                                       on its own shape, and (b) its
                                       `:grade-actual` claim actually
                                       clears THIS actor's own
                                       disclosed acceptance floor for
                                       upstream non-ferrous-ore
                                       feedstock (`min-upstream-ore-
                                       grade-pct`, below). When
                                       `:upstream-ore-pedigree` is
                                       ABSENT this check is a NO-OP --
                                       existing proposals with no
                                       upstream linkage continue to
                                       dispatch exactly as before this
                                       ADR (additive, never a breaking
                                       change).
    7. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       dispatch-process-step`/
                                       `:actuation/finalize-yield-
                                       audit` (REAL safety/business-
                                       critical acts) -> escalate.

  Two more guards, double-dispatch/double-finalization prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-dispatched-
  violations`/`already-audited-violations` refuse to dispatch a
  process step/finalize a yield audit for the SAME lot twice, off
  dedicated `:process-step-dispatched?`/`:yield-audit-finalized?`
  facts (never a `:status` value) -- the SAME 'check a dedicated
  boolean, not status' discipline every prior sibling governor's
  guards establish, informed by `cloud-itonami-isic-6492`'s status-
  lifecycle bug (ADR-2607071320).

  Addendum (superproject part-supplier-linkage ADR, cloud-itonami-
  isic-2610<->cloud-itonami-isic-2813): a NINTH HARD check,
  `handoff-incomplete-violations`, was added alongside an OPTIONAL
  `:handoff` field on `:actuation/finalize-yield-audit` (the
  superproject `:handoff` shared shape, ADR-2607177600, reused as-is
  -- no new shape; `register-yield-audit`'s own docstring already
  says a finalized yield audit is 'ahead of shipment/certification',
  making this the natural dispatch-like attachment point in this
  actor, DISTINCT from `:actuation/dispatch-process-step`, which is
  an INTERNAL cleanroom robot action, never an outbound handoff, and
  also DISTINCT from `:upstream-ore-pedigree` above: that names an
  UPSTREAM feedstock claim this actor independently re-verifies
  before accepting, `:handoff` names a DOWNSTREAM consumer this
  actor's own finalized lot is destined for). `:handoff` names which
  downstream consumer (e.g. cloud-itonami-isic-2813, sourcing
  `part:control-panel` electronic components) the finalized lot is
  destined for; unlike isic-1075's own `:coordinate-shipment` (which
  made `:handoff` MANDATORY), this actor's `:handoff` stays OPTIONAL
  -- a finalization with NO `:handoff` at all is NOT a violation, but
  a `:handoff` that IS present and missing any of its own three
  identity/correlation fields (`registry/handoff-fields-present?`)
  HARD-holds."
  (:require [fab.facts :as facts]
            [fab.registry :as registry]
            [fab.robotics :as robotics]
            [fab.store :as store]
            [kotoba.pedigree :as pedigree]))

(def confidence-floor 0.6)

(def ^:const min-upstream-ore-grade-pct
  "Real, disclosed minimum acceptable upstream non-ferrous-ore
  feedstock grade (percent commodity content -- a `kotoba.pedigree`
  `:grade-actual` claim from a `cloud-itonami-isic-0729`-issued
  pedigree, ADR-2607999980) this actor requires before accepting an
  incoming ore production record as suitable feedstock ancestry for a
  fab lot.

  `nonferrousops.export`'s own docstring discloses that
  `:grade-actual` is reported EXACTLY as recorded, with no cross-
  commodity normalization -- `nonferrousops.facts/commodities`
  covers five structurally different commodities (copper, lithium,
  nickel, cobalt, rare-earth) whose grade-reporting conventions are
  not directly comparable. This floor is calibrated for COPPER
  specifically -- `nonferrousops.sim`'s own demo defaults to a
  `:copper` commodity, and refined copper is this fab's own
  practically relevant non-ferrous-ore-derived input (bonding-wire/
  interconnect metallization) -- so this check is only a meaningful
  acceptance gate when the upstream pedigree's evidence-basis in fact
  names copper; this ns does not itself parse the evidence-basis
  string to enforce that, mirroring how `kotoba.pedigree` itself
  makes no claim about what a claim key means (see that ns's own
  docstring).

  Set at a commonly-cited copper-concentrate smelter-feed floor:
  flotation-processed copper concentrate commonly runs in the
  20%-30% Cu range, and roughly 20% Cu is a commonly-cited practical
  minimum smelter-acceptable concentrate grade (below which
  processing/freight economics become marginal) -- a commonly-cited
  generic threshold, not a literal transcription of one specific
  named standard's number, the SAME disclosed-prior-allowance style
  `steelworks.governor/min-upstream-ore-grade-pct` already uses for
  ITS OWN cross-actor acceptance floor in the automotive chain."
  20.0)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real robot process-step action in the cleanroom and
  finalizing a real yield-audit record are the two real-world
  actuation events this actor performs -- a two-member set, matching
  every prior dual-actuation sibling's shape. Both are POSITIVE
  actuations (issuing/finalizing a record), matching this fleet's
  majority actuation shape."
  #{:actuation/dispatch-process-step :actuation/finalize-yield-audit})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:requirements/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  process-safety requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:requirements/verify :actuation/dispatch-process-step :actuation/finalize-yield-audit} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は工程安全基準として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/dispatch-process-step`/`:actuation/finalize-yield-
  audit`, the jurisdiction's required process-spec-verification-
  record/EDA-CAE-simulation-record/chemical-safety-clearance-record/
  wafer-lot-traceability-record evidence must actually be satisfied --
  do not trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/dispatch-process-step :actuation/finalize-yield-audit} op)
    (let [l (store/lot st subject)
          verification (store/verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction l) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(工程仕様検証記録/EDA・CAEシミュレーション記録/化学物質安全許可記録/ウェハーロット追跡記録等)が充足していない状態での提案"}]))))

(defn- robotics-simulation-violations
  "For `:actuation/dispatch-process-step`: HARD hold if the robot
  wafer-probe/optical-inspection/wire-bond-pull-test verification
  mission (`fab.robotics`) never ran and was recorded on the lot
  (`:robotics-sim-verified?`), OR if it did but an INDEPENDENT
  recompute of the lot's own REAL `fab.simphysics`-simulated
  bond-pull-strength fields (ADR-2607152000 -- `fab.robotics/
  simulation-out-of-tolerance?`) says out-of-tolerance right now --
  never trusts the mission's own stored :passed? verdict alone, the
  same discipline `yield-rate-insufficient-violations` below uses for
  yield rate."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-process-step)
    (let [l (store/lot st subject)]
      (cond
        (not (:robotics-sim-verified? l))
        [{:rule :robotics-simulation-missing
          :detail (str subject " のウェハープローブ/ワイヤーボンド検証ミッションが未実行・未合格")}]

        (robotics/simulation-out-of-tolerance? l)
        [{:rule :robotics-simulation-out-of-tolerance
          :detail (str subject " の実測ワイヤーボンドプル強度(physics-2d実時間ステップ再検証, 線径"
                       (:bond-wire-diameter-um l) "μm→" (:bond-pull-strength-actual l)
                       "gf)が独立再検証で許容範囲["
                       (:bond-pull-strength-min l) "," (:bond-pull-strength-max l) "]を逸脱")}]))))

(defn- process-defect-flag-unresolved-violations
  "An unresolved process-defect flag -- reported by THIS proposal (e.g.
  a `:defect/screen` that itself just found one), or already on file
  in the store for the lot (`:defect/screen`/`:actuation/dispatch-
  process-step`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        lot-id (when (contains? #{:defect/screen :actuation/dispatch-process-step} op) subject)
        hit-on-file? (and lot-id (= :unresolved (:verdict (store/defect-screen-of st lot-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :process-defect-flag-unresolved
        :detail "未解決の工程欠陥フラグがある状態での工程実行提案は進められない"}])))

(defn- yield-rate-insufficient-violations
  "For `:actuation/finalize-yield-audit`, INDEPENDENTLY recompute
  whether the lot's own yield rate falls below its own required
  threshold via `fab.registry/yield-rate-insufficient?` -- needs no
  proposal inspection or stored-verdict lookup at all, since its
  inputs are permanent ground-truth fields already on the lot."
  [{:keys [op subject]} st]
  (when (= op :actuation/finalize-yield-audit)
    (let [l (store/lot st subject)]
      (when (registry/yield-rate-insufficient? l)
        [{:rule :yield-rate-insufficient
          :detail (str subject " の歩留まり(" (:good-dies l) "/" (:total-dies l)
                      ")が必要基準(" (:required-yield-share l) ")を下回る")}]))))

(defn- upstream-ore-pedigree-claims-out-of-tolerance-violations
  "ADR-2607999980's smartphone-chain applied link of the
  ADR-2607999950 cross-actor supply-chain-linkage pattern (direct
  port of ADR-2607999970's `steelworks.governor/upstream-ore-
  pedigree-claims-out-of-tolerance-violations`). For `:actuation/
  dispatch-process-step`: when the lot carries an OPTIONAL
  `:upstream-ore-pedigree` (a `kotoba.pedigree` record an upstream
  `cloud-itonami-isic-0729` non-ferrous-ore production record issued
  via `nonferrousops.export/pedigree-for-production-record` and a
  test/demo/orchestration script attached to this lot as plain EDN
  data -- never a live network call), INDEPENDENTLY re-verify it,
  never trusting the upstream actor's claim at face value: (a) the
  pedigree's own shape (`kotoba.pedigree/valid?`) -- a malformed/
  incomplete pedigree is never accepted, and (b) its `:grade-actual`
  claim actually clears THIS actor's own disclosed acceptance floor
  for upstream non-ferrous-ore feedstock (`min-upstream-ore-grade-
  pct`) -- the SAME 'ground truth, not self-report' discipline
  `robotics-simulation-violations`/`yield-rate-insufficient-
  violations` above already apply WITHIN this actor, now extended
  ACROSS actors.

  When `:upstream-ore-pedigree` is ABSENT this check is a NO-OP --
  existing proposals with no upstream linkage continue to dispatch
  exactly as before this ADR (additive, never a breaking change)."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-process-step)
    (let [l (store/lot st subject)
          p (:upstream-ore-pedigree l)]
      (when (some? p)
        (cond
          (not (pedigree/valid? p))
          [{:rule :upstream-ore-pedigree-invalid-shape
            :detail (str subject " のupstream ore pedigreeがkotoba.pedigreeの形状検証に失敗")}]

          (let [v (pedigree/claim-value p :grade-actual)]
            (or (not (number? v)) (< v min-upstream-ore-grade-pct)))
          [{:rule :upstream-ore-pedigree-claims-out-of-tolerance
            :detail (str subject " のupstream ore pedigree(" (:pedigree/id p)
                        ")の鉱石品位(" (pedigree/claim-value p :grade-actual)
                        "%)が受入基準(" min-upstream-ore-grade-pct "%)を下回る")}])))))

(defn- already-dispatched-violations
  "For `:actuation/dispatch-process-step`, refuses to dispatch a
  process step for the SAME lot twice, off a dedicated `:process-
  step-dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-process-step)
    (when (store/lot-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に工程実行済み")}])))

(defn- already-audited-violations
  "For `:actuation/finalize-yield-audit`, refuses to finalize a yield
  audit for the SAME lot twice, off a dedicated `:yield-audit-
  finalized?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/finalize-yield-audit)
    (when (store/lot-already-audited? st subject)
      [{:rule :already-audited
        :detail (str subject " は既に歩留まり監査確定済み")}])))

(defn- handoff-incomplete-violations
  "For `:actuation/finalize-yield-audit`, `:handoff` (the superproject
  `:handoff` shared shape, ADR-2607177600, reused as-is) is entirely
  OPTIONAL -- a finalization with NO `:handoff` at all is NOT a
  violation (this fab ships lots to any customer, tracked or not, the
  same 'optional field absent -> not checked' discipline
  cloud-itonami-isic-2710's own `:coordinate-shipment`-`:handoff`
  extension uses). But a `:handoff` that IS present and missing any of
  its own three identity/correlation fields
  (`registry/handoff-fields-present?`) is a fabricated/incomplete
  reference -- HARD hold, the same anti-fabrication discipline
  `upstream-ore-pedigree-claims-out-of-tolerance-violations` above
  applies to an upstream pedigree reference, applied here to a
  downstream handoff reference."
  [{:keys [op]} proposal]
  (when (= op :actuation/finalize-yield-audit)
    (when-let [handoff (:handoff (:value proposal))]
      (when-not (registry/handoff-fields-present? handoff)
        [{:rule :handoff-incomplete
          :detail "handoff参照が付与されているが必須フィールド(:handoff/id・:handoff/source-actor・:handoff/batch-id)が不足 -- 架空/不完全なhandoff参照では歩留まり監査を確定できない"}]))))

(defn check
  "Censors a Fab Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}.

  Includes `handoff-incomplete-violations` -- a NINTH hard check
  added alongside the OPTIONAL `:handoff` field on `:actuation/
  finalize-yield-audit` (see ns docstring Addendum), purely additive:
  it only ever fires for `:actuation/finalize-yield-audit` proposals
  that carry a `:handoff` map, and is a no-op for every pre-existing
  caller that never sets `:handoff` at all."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (robotics-simulation-violations request st)
                           (process-defect-flag-unresolved-violations request proposal st)
                           (yield-rate-insufficient-violations request st)
                           (upstream-ore-pedigree-claims-out-of-tolerance-violations request st)
                           (already-dispatched-violations request st)
                           (already-audited-violations request st)
                           (handoff-incomplete-violations request proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
