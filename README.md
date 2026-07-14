# cloud-itonami-isic-2610

Open Business Blueprint for **ISIC Rev.5 2610**: semiconductor and
electronics manufacturing enablement -- fab and test operations for
small-batch/legacy nodes.

This repository publishes a semiconductor-fab actor -- lot intake,
process-safety requirements verification, process-defect screening,
process-step dispatch and yield-audit finalization -- as an OSS
business that any qualified fab operator can fork, deploy, run,
improve and sell, so a community keeps its own operating records
instead of renting a closed MES/SaaS.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020),
[`9420`](https://github.com/cloud-itonami/cloud-itonami-isic-9420),
[`9491`](https://github.com/cloud-itonami/cloud-itonami-isic-9491)) --
the SECOND manufacturing vertical in this fleet (after `3030`'s
aerospace assembly), with a genuinely distinct domain concern: wafer-
lot yield sufficiency and cleanroom process-defect containment rather
than airframe-assembly dimensional tolerance. Here it is **Fab Advisor
⊣ Fab Operations Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a lot-
> intake summary, normalizing records, and checking whether a lot's
> own measured yield rate actually reaches its own required
> threshold -- but it has **no notion of which jurisdiction's fab
> process-safety requirements are official, no license to dispatch a
> real robot process-step action in the cleanroom or finalize real
> yield-audit records, and no way to know on its own whether a
> process-defect flag against the lot has actually stayed
> unresolved**. Letting it dispatch a process step or finalize a
> yield audit directly invites fabricated process-safety citations, a
> contaminated/defective lot proceeding to the next process step, and
> an insufficient yield rate being quietly certified as acceptable --
> and liability, and safety/business risk, for whoever runs it. This
> project seals the Fab Advisor into a single node and wraps it with
> an independent **Fab Operations Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers lot intake through process-safety requirements
verification, process-defect screening, process-step dispatch and
yield-audit finalization. It does **not**, by itself, hold any
operating license required to run a semiconductor fab in a given
jurisdiction, and it does not claim to. It also does **not** model a
real fab/MES (manufacturing execution system) control system, a real
robot motion-planning/process-tool-control stack, or a full EDA/CAE
simulation engine -- no direct hardware dispatch protocol, no finite-
element/process-modeling solver (see `fab.facts`'s own docstring for
the honest simplification this makes: a starting catalog of process-
safety authorities, not a survey of every jurisdiction's chemical/
gas-handling-regulation variant). Whoever deploys and operates a live
instance (a licensed fab operator) supplies any jurisdiction-specific
license, the real process/manufacturing engineering and the real EDA/
CAE/MES tooling integrations, and bears that jurisdiction's liability
-- the software supplies the governed, spec-cited, audited execution
scaffold so that operator does not have to build the compliance layer
from scratch for every new lot.

### Actuation

**Dispatching a real robot process-step action in the cleanroom or
finalizing a real yield-audit record is never autonomous, at any
phase, by construction.** Two independent layers enforce this (`fab.
governor`'s `:actuation/dispatch-process-step`/`:actuation/finalize-
yield-audit` high-stakes gate and `fab.phase`'s phase table, which
never puts `:actuation/dispatch-process-step`/`:actuation/finalize-
yield-audit` in any phase's `:auto` set) -- see `fab.phase`'s
docstring and `test/fab/phase_test.clj`'s `dispatch-process-step-
never-auto-at-any-phase`/`finalize-yield-audit-never-auto-at-any-
phase`. The actor may draft, check and recommend; a human fab
engineer is always the one who actually dispatches a process step or
finalizes a yield audit. Like `6512`/`6622`/`6520`/`6530`/`6820`/
`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/
`8510`/`9412`/`8720`/`8521`/`6619`/`3600`/`6190`/`3030`/`3830`/`9420`/
`9491`, this actor has TWO actuation events, both POSITIVE (issuing/
finalizing a real record), matching the majority pattern in this
fleet (`3600`/`6190` are the fleet's two NEGATIVE-actuation
exceptions).

## The core contract

```
lot intake + jurisdiction facts (fab.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Fab          │ ─────────────▶ │ Fab                           │  (independent system)
   │ Advisor      │  + citations    │ Operations Governor:         │
   │ (sealed)     │                 │ spec-basis · evidence-       │
   └──────────────┘         commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ process-defect-
                           record + ledger  escalate ─▶ human   flag-unresolved
                                             (ALWAYS for         (unconditional) ·
                                              :actuation/dispatch-      yield-rate-
                                              process-step /            insufficient
                                              :actuation/finalize-       (ratio) ·
                                              yield-audit)                already-dispatched/
                                                                           -audited
```

**The Fab Advisor never dispatches a process step or finalizes a
yield audit the Fab Operations Governor would reject, and never does
so without a human sign-off.** Hard violations (fabricated process-
safety requirements; unsupported evidence; an unresolved process-
defect flag; an insufficient yield rate; a double dispatch or
finalization) force **hold** and *cannot* be approved past; a clean
dispatch/audit proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

A live sample of the operator console (robotics safety console, shared
template) is rendered in
[docs/samples/operator-console.html](docs/samples/operator-console.html)
-- pure-data HTML output of `kotoba.robotics.ui`.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a fab robot handles wafer
transport, process-step execution and inspection in the cleanroom
under the actor, gated by the independent **Fab Operations Governor**.
The governor never dispatches hardware itself; `:high`/`:safety-
critical` actions (such as handling wafers, chemicals and expensive/
in-critical-supply lots) require human sign-off.

**Robot process simulation is concrete, not just a flag** (ADR-2607142800/
ADR-2607150500, extending ADR-2607011000): `fab.robotics` walks every
lot through a robot-executed wafer-probe/optical-inspection/wire-bond
verification mission (`kotoba.robotics` mission/action/telemetry-proof
contracts) -- automated wafer-probe electrical test, robotic optical-
defect inspection scan, automated wire-bond pull-test -- before
`:actuation/dispatch-process-step` is proposable.

**This is now a REAL, time-stepped physics simulation, not a
synthetic field comparison (ADR-2607152000, generalizing
ADR-2607151600's automotive pilot to this vertical).** This repository
takes a REAL git-coordinate dependency on
[`kotoba-lang/physics-2d`](https://github.com/kotoba-lang/physics-2d)
(pinned by SHA in `deps.edn`), and `fab.robotics/simulate-process-step`
actually calls it via `fab.simphysics` (built DIRECTLY inside this
repo -- fab has no sibling `kami-engine-*` design repo the way
automotive pairs with `kami-engine-vehicle-designer`): a real
time-stepped rigid-body simulation of the wire-bond destructive
PULL test -- the lot's own `:bond-wire-diameter-um` becomes an actual
`Body2D` (a mass-scaling abstraction proportional to the wire's real
cross-sectional area) pulled at a real, representative pull-tester
crosshead rate until it collides with a static, virtual
"tension-limit" boundary positioned at the wire's own representative
compliant-travel distance -- the real per-tick impulse `physics-2d`
resolves at that stopping event is this actor's real
`:bond-pull-strength-actual` reading. The Fab Operations Governor
independently re-derives that REAL simulated reading against this
actor's own UNCHANGED, already-established
[:bond-pull-strength-min :bond-pull-strength-max] = [6.0 12.0] gf
tolerance band, never trusting the mission's self-reported verdict
alone. Honest scope: `physics-2d` only resolves collisions, so a pull
(tension) event is modeled as its physical inverse -- an anchor
travelling to, and stopping at, the wire's own tension limit -- and
the pull-rate/travel-distance/mass-scaling constants are disclosed
engineering priors, not independently-certified metrology; see
`fab.simphysics`'s namespace docstring for the full, disclosed
derivation. This real-engine wiring is scoped to fab (and, separately,
to automotive) only; the other cloud-itonami manufacturing actors
touched by ADR-2607142800 remain on the symbolic robotics-simulation
layer until a similar integration is built for each.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Fab Operations Governor, process-step-dispatch + yield-audit draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`2610`). Required capabilities:

- `:robotics`
- `:eda`
- `:cae`
- `:dmn`
- `:bpmn`
- `:audit-ledger`

## Layout

| File | Role |
|---|---|
| `src/fab/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate process-step-dispatch/yield-audit history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded lot, and the double-actuation guards check dedicated `:process-step-dispatched?`/`:yield-audit-finalized?` booleans rather than a `:status` value |
| `src/fab/registry.cljc` | Process-step-dispatch + yield-audit draft records, plus `yield-rate-insufficient?` -- the FOURTH instance of this fleet's ratio-based check family (`leasing`/`behavioral`/`union` established the first three), MINIMUM-floor direction |
| `src/fab/facts.cljc` | Per-jurisdiction fab process-safety catalog (national chemical/gas-handling regulator + SEMI international standards) with an official spec-basis citation per entry, honest coverage reporting |
| `src/fab/fabadvisor.cljc` | **Fab Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/verification/defect-screening/robotics-simulation/process-step-dispatch/yield-audit proposals |
| `src/fab/robotics.cljc` | Robot wafer-probe/optical-inspection/wire-bond-pull-test verification mission (`kotoba.robotics` mission/action/telemetry-proof), `bond-pull-strength-out-of-range?` ground truth + `simulation-out-of-tolerance?` independent recheck for the governor (ADR-2607142800/ADR-2607150500), now backed by a REAL `physics-2d` time-stepped simulation (ADR-2607152000) |
| `src/fab/simphysics.cljc` | **REAL** time-stepped rigid-body wire-bond pull-test simulation on `kotoba-lang/physics-2d`'s real impulse solver -- derives `:bond-pull-strength-actual` from an actual simulated trajectory, not a hand-set field (ADR-2607152000) |
| `src/fab/governor.cljc` | **Fab Operations Governor** -- 5 HARD checks (spec-basis · evidence-incomplete · robotics-simulation missing/independently-out-of-tolerance (new, ADR-2607150500) · process-defect-flag-unresolved, unconditional evaluation, the THIRTY-SECOND grounding of this discipline · yield-rate-insufficient, pure ground-truth ratio recompute) + already-dispatched/already-audited guards + 1 soft (confidence/actuation gate) |
| `src/fab/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both process-step dispatch and yield-audit finalization always human; lot intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/fab/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/fab/sim.cljc` | demo driver |
| `test/fab/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers lot intake through process-safety requirements
verification, process-defect screening, process-step dispatch and
yield-audit finalization -- the core governed lifecycle this
blueprint's own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Lot intake + per-jurisdiction process-safety checklisting, HARD-gated on an official spec-basis citation (`:lot/intake`/`:requirements/verify`) | Real fab/MES control-system integration, real robot motion-planning/process-tool control (see `fab.facts`'s docstring) |
| Process-defect screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:defect/screen`) | Real EDA/CAE finite-element/process-modeling simulation engine |
| Robot wafer-probe/optical-inspection/wire-bond-pull-test verification mission, required on file (and independently re-checked out-of-tolerance against a REAL `physics-2d` time-stepped simulation, ADR-2607152000) before process-step dispatch (`:robotics/simulate-process-step`) | Real robot-cell telemetry integration; a real 3D solver (`physics-2d` is a 2D projection); real EDA/CAE finite-element process modeling (`fab.simphysics` remains a deterministic, disclosed-simplification simulation -- see its own docstring) |
| Process-step dispatch, HARD-gated on full evidence plus the robotics-simulation mission, plus a double-dispatch guard (`:actuation/dispatch-process-step`) | Fab operating-license application processes themselves |
| Yield-audit finalization, HARD-gated on full evidence and yield-rate sufficiency, plus a double-finalization guard (`:actuation/finalize-yield-audit`) | |
| Immutable audit ledger for every intake/verification/screening/dispatch/finalization decision | |

Extending coverage is additive: add the next gate (e.g. a chemical-
inventory-reconciliation check) as its own governed op with its own
HARD checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`fab.facts/coverage` reports how many requested jurisdictions actually
have an official spec-basis in `fab.facts/catalog` -- currently 4
seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions worldwide. This
is a starting catalog to prove the governor contract end-to-end, not a
claim of global coverage. Adding a jurisdiction is additive: one map
entry in `fab.facts/catalog`, citing a real official source -- never
fabricate a jurisdiction's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `Fab Advisor` + `Fab Operations Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the forty-
seven prior actors' architecture. See `docs/adr/0001-architecture.md`
for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
