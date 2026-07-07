# ADR-0001: Fab Advisor ⊣ Fab Operations Governor architecture

## Status

Accepted. `cloud-itonami-isic-2610` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-2610` publishes an OSS business blueprint for
semiconductor and electronics manufacturing enablement: fab and test
operations for small-batch/legacy nodes, run by a qualified operator
so a community keeps its own operating records instead of renting a
closed MES/SaaS. Like every prior actor in this fleet, the blueprint
alone is not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the
same langgraph-clj StateGraph + independent Governor + Phase 0→3
rollout pattern established by `cloud-itonami-isic-6511` (life
insurance) and applied across forty-seven prior siblings, most
recently `cloud-itonami-isic-9491` (religious organizations).

## Decision

### Decision 1: this fleet's SECOND manufacturing vertical, a genuinely distinct domain concern

Following `3030` (aerospace assembly, this fleet's first manufacturing
vertical), `cloud-itonami-isic-2610` is the SECOND. The domain concern
here is genuinely distinct from `3030`'s: semiconductor fab operation
centers on wafer-lot YIELD sufficiency (a statistical business/
quality metric) and cleanroom PROCESS-DEFECT containment (a
contamination-control safety concern), rather than airframe-assembly
dimensional tolerance and NDT-defect detection. This distinction
justifies choosing genuinely different check families rather than
reusing `3030`'s two-sided range check.

### Decision 2: entity and op shape

The primary entity is a `lot` (a wafer/semiconductor lot under
manufacture, analogous to `aerospace.store`'s `assembly`). Five ops:
`:lot/intake` (directory upsert, no capital risk), `:requirements/
verify` (per-jurisdiction process-safety evidence checklist, never
auto -- analogous to `aerospace.operation`'s requirements-verification
concept), `:defect/screen` (process-defect screening, unconditional-
evaluation discipline, never auto), `:actuation/dispatch-process-step`
(POSITIVE, high-stakes -- dispatching the real robot process-step
action in the cleanroom), and `:actuation/finalize-yield-audit`
(POSITIVE, high-stakes -- finalizing the real yield-audit record).
This matches the dual-actuation-on-one-entity shape every recent
dual-actuation sibling uses.

### Decision 3: `yield-rate-insufficient?` -- the 4th ratio-based check, MINIMUM-floor direction

Following `leasing.registry/collateral-coverage-ratio-insufficient?`
(1st, MINIMUM-floor), `behavioral.registry/supervision-ratio-
insufficient?` (2nd, MAXIMUM-ceiling) and `union.registry/strike-
vote-share-insufficient?` (3rd, MINIMUM-floor), `fab.registry/yield-
rate-insufficient?` applies the SAME quotient-comparison shape --
MINIMUM-floor direction -- to a lot's own good-dies count divided by
its own total-dies count against its own required-yield-share
threshold. This is a direct, natural mapping onto real semiconductor
fab practice: yield-rate thresholds gate shipment/certification
decisions industry-wide. It gates only `:actuation/finalize-yield-
audit` -- the point where an insufficient-yield lot would otherwise be
certified as acceptable.

### Decision 4: `process-defect-flag-unresolved-violations` -- the 32nd unconditional-evaluation screening grounding

Following the discipline `casualty.governor/sanctions-violations`
established and thirty-one prior siblings (most recently
`congregation.governor/safeguarding-concern-unresolved-violations`,
the 31st) have applied, `process-defect-flag-unresolved-violations`
is evaluated UNCONDITIONALLY -- not scoped to a specific op -- so
`:defect/screen` itself can HARD-hold on its own finding. This is the
32nd distinct grounding of this exact discipline. Gates `:defect/
screen` and `:actuation/dispatch-process-step` specifically (not
`:actuation/finalize-yield-audit`) -- matching this blueprint's own
published Trust Controls, which name "process steps outside spec are
blocked" and "a robot action the governor refuses is never dispatched
to hardware" as the concerns tied to the physical dispatch act, while
yield-rate sufficiency (Decision 3) is the concern tied to the
certification act. Exercised in tests/demo via `:defect/screen`
DIRECTLY against an already-flagged lot, not via an actuation op
against an unscreened lot -- the "screen the screening op directly,
not the actuation op" lesson `parksafety`'s ADR-2607071922 Decision 5
established, now applied for a TWENTY-SECOND consecutive sibling
(`facility`=8th, `school`=9th, `association`=10th, `leasing`=11th,
`behavioral`=12th, `secondary`=13th, `card`=14th, `water`=15th,
`telecom`=16th, `aerospace`=17th, `recovery`=18th, `consulting`=19th,
`union`=20th, `congregation`=21st, `fab`=22nd).

### Decision 5: dedicated double-actuation-guard booleans

`:process-step-dispatched?`/`:yield-audit-finalized?` are dedicated
booleans on the `lot` record, never a single `:status` value -- the
same discipline every prior sibling governor's guards establish,
informed by `cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 6: Store protocol, MemStore + DatomicStore parity

`fab.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-
backed), proven to satisfy the same contract in `test/fab/
store_contract_test.clj` -- the same seam every sibling actor uses so
swapping the SSoT backend is a configuration change, not a rewrite.

### Decision 7: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:lot/intake` (no
capital risk). `:requirements/verify` and `:defect/screen` are never
auto-eligible at any phase (matching every sibling's screening-op
posture), and `:actuation/dispatch-process-step`/`:actuation/
finalize-yield-audit` are permanently excluded from every phase's
`:auto` set -- a structural fact, not a rollout milestone, enforced by
BOTH `fab.phase` and `fab.governor`'s `high-stakes` set
independently.

### Decision 8: mock + LLM advisor pair

`fab.fabadvisor` provides `mock-advisor` (deterministic, default
everywhere -- the actor graph and governor contract run offline) and
`llm-advisor` (backed by `langchain.model/ChatModel`, with a defensive
EDN-proposal parser so a malformed LLM response degrades to a safe
low-confidence noop rather than ever auto-dispatching a process step
or auto-finalizing a yield audit).

### Decision 9: blueprint.edn field-sync fixes

Two stale-scaffold inconsistencies in `blueprint.edn`, discovered
during the standard "survey blueprint scaffold" step before writing
any code, were fixed as part of this promotion (the same class of fix
`card.6619`'s, `water.3600`'s, `telecom.6190`'s and `aerospace.3030`'s
own ADR-0001s document):

1. `:itonami.blueprint/id` was the stale pre-rename value
   `"cloud-itonami-2610"` (missing `isic-`), while the repo folder,
   README title and this actor's own `:business-id` already use the
   corrected `cloud-itonami-isic-2610`. Fixed to match.
2. `:itonami.blueprint/optional-technologies` was missing entirely
   despite the `kotoba-lang/industry` registry's own entry for `"2610"`
   already stating `[:cfd :telemetry :optimization]`. Fixed to match
   the registry exactly.

## Alternatives considered

- **Reusing `aerospace.registry/assembly-tolerance-out-of-range?`'s
  two-sided range shape for a process-parameter check.** Considered,
  but rejected in favor of the yield-rate RATIO check: yield (good-
  dies/total-dies against a minimum threshold) is the more
  domain-canonical, universally-recognized semiconductor-fab quality
  gate, and choosing it over a generic range check demonstrates the
  ratio-check family generalizing to a genuinely new domain rather
  than defaulting to whichever family was used most recently.
- **Gating `yield-rate-insufficient?` at `:actuation/dispatch-
  process-step` instead of `:actuation/finalize-yield-audit`.**
  Rejected: yield is a POST-manufacturing measured outcome (dies are
  counted after fabrication), so it is only meaningful to gate the
  yield-AUDIT act, not the earlier physical dispatch act -- gating
  dispatch on a not-yet-measured yield figure would be a category
  error.
- **A single unified "lot-quality-insufficient" check merging yield-
  rate and process-defect concerns.** Rejected: yield rate is a
  ground-truth numeric recompute needing no proposal inspection;
  process-defect status is an unconditionally-evaluated flag that
  must also HARD-hold the screening op itself on its own finding --
  merging them would either lose the screening op's self-hold
  property or force a numeric check to behave like a flag check,
  both worse than keeping them as the two distinct, well-established
  check-family shapes this fleet already uses.

## Consequences

- Forty-eighth actor in this fleet (47 implemented before this
  build), and the SECOND manufacturing vertical, with a genuinely
  distinct set of domain-specific checks from the first.
- Confirms the ratio-based check family generalizes to a fourth,
  genuinely distinct domain (semiconductor yield sufficiency),
  following `leasing`/`behavioral`/`union`.
- Confirms the unconditional-evaluation screening discipline
  generalizes to fab-cleanroom process-defect containment, its 32nd
  grounding.
- Two pre-existing `blueprint.edn` inconsistencies (stale ID, missing
  `:optional-technologies`) fixed as in-scope minor consistency work,
  consistent with how `card.6619`/`water.3600`/`telecom.6190`/
  `aerospace.3030` handled the same class of issue.
