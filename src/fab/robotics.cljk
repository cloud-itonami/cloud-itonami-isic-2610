(ns fab.robotics
  "Robot-executed cleanroom process-step verification -- the concrete,
  actor-level realization of ADR-2607011000's robotics premise (every
  cloud-itonami vertical is designed on the premise that a robot
  performs the physical-domain work; an independent governor gates any
  action before it ever reaches hardware) for THIS actor's own
  `fab.facts` requirement that a process-step-dispatch proposal cite an
  EDA-CAE-simulation-record actually on file -- not merely a self-
  reported checklist string.

  ADR-2607152000 (generalizing ADR-2607151600's automotive pilot to
  this vertical) rewires this ns onto a REAL engineering simulation
  instead of a synthetic, deterministic field comparison: `fab.
  simphysics` (a genuine time-stepped rigid-body simulation of the
  wire-bond destructive pull test, built on the real `kotoba-lang/
  physics-2d` impulse solver) is actually called here -- this is a REAL
  dependency (see deps.edn), not an opaque EDN payload. Unlike
  automotive (which routes through a separate sibling design repo,
  `kami-engine-vehicle-designer`), fab has no such sibling repo, so
  `fab.simphysics` lives DIRECTLY inside this actor.

  A robot mission (`kotoba.robotics/mission`) walks the lot through
  three :sense/:actuate steps -- automated wafer-probe electrical
  test, robotic optical-defect inspection scan, automated wire-bond
  pull-test -- built with `kotoba.robotics/action` + `kotoba.robotics/
  telemetry-proof`, and reports an overall :passed? verdict now
  derived from the REAL simulated pull-test trajectory
  (`:bond-pull-strength-actual`, see `bond-pull-telemetry-for`), not a
  hand-set field. `simulation-out-of-tolerance?` independently re-
  derives that verdict from the lot's OWN recorded bond-pull-strength
  fields, never from the mission's self-reported result -- the SAME
  'ground truth, not self-report' discipline `fab.registry/yield-rate-
  insufficient?` established for yield rate (this fleet's ratio-based
  check family; see that ns's docstring for prior siblings -- this
  ns's `bond-pull-strength-out-of-range?` is instead a two-sided range
  check, the SAME shape `automotive.registry/vehicle-emissions-out-of-
  range?` and siblings use, applied here to a lot's own REAL simulated
  wire-bond pull-strength against its own recorded tolerance bounds --
  those bounds, `:bond-pull-strength-min`/`:bond-pull-strength-max`,
  are UNCHANGED and unmoved by this ADR: this actor's own existing,
  already-established real-world-flavored spec, never an invented
  number). `fab.governor`'s `robotics-simulation-violations` calls this
  ns's independent recheck, never the stored :passed? value, before any
  `:actuation/dispatch-process-step` proposal may commit.

  Honest scope (ADR-2607152000): the pull-test physics is a 2D
  projection AND a reinterpretation of a collision-only engine as a
  tension event (see `fab.simphysics`'s own docstring for the full,
  disclosed derivation -- the pull-rate/travel-distance constants and
  the wire-diameter-to-mass scaling are disclosed engineering priors,
  not independently-certified metrology). What IS real: an actual
  `physics-2d/world-step` tick-by-tick rigid-body trajectory, and a
  real, non-fabricated peak force reading derived directly from it.

  Pure data + pure functions -- no real robot I/O, no network.
  `fab.simphysics` and `kotoba.robotics` are themselves pure data
  transforms (`physics-2d`'s own `world-step` is a pure fixed-timestep
  integrator, no wall-clock/IO), so this stays exactly as offline/
  deterministic as every other sibling namespace in this actor --
  tests and the demo run without a network.

  ADR-2607992500 EXTENDS this vertical with `fab.cad`/`fab.scene`/
  `fab.motionplan` -- the BOM/motion-plan/scene bridge this ns's own
  docstring used to disclose as absent (ADR-2607152000's then-narrower
  scope). `design-for` below now threads through the SAME optional
  `:specimen-*-mm` fields `fab.cad`/`fab.simphysics` read, so this
  ns's own telemetry path stays consistent with what `fab.scene`/
  `fab.motionplan` (which read a lot's `:specimen-*-mm` fields
  directly, not through `design-for`) would derive for the same lot."
  (:require [kotoba.robotics :as robotics]
            [fab.simphysics :as simphysics]))

(def mission-actions
  "The three-step wafer-probe/optical-inspection/wire-bond-pull-test
  verification mission every lot walks through before `:actuation/
  dispatch-process-step` is proposable. All :sense/:actuate at
  :none/:low safety -- verification/QA sensing on a lot in the
  cleanroom, not the robot process-step actuation that is `:actuation/
  dispatch-process-step` itself (always :safety-critical -- see
  `fab.governor`)."
  [{:step :wafer-probe-electrical-test    :kind :sense   :safety :none}
   {:step :optical-defect-inspection-scan :kind :sense   :safety :none}
   {:step :wire-bond-pull-test            :kind :actuate :safety :low}])

(defn design-for
  "The `fab.simphysics` design-record inputs a governed lot's own
  permanent, recorded fields map to -- exactly the fields
  `fab.simphysics/simulate` actually reads: `:bond-wire-diameter-um`
  (mass-scaling), plus (ADR-2607992500) the optional
  `:specimen-length-mm`/`:specimen-width-mm`/`:specimen-height-mm`
  CAD-envelope triple `fab.cad/envelope-dims-mm` reads when a lot
  carries a real test-specimen measurement on file. Pure: a plain
  select, no derived/ephemeral inputs needed."
  [lot]
  (select-keys lot [:bond-wire-diameter-um
                     :specimen-length-mm :specimen-width-mm :specimen-height-mm]))

(defn bond-pull-telemetry-for
  "Runs the REAL `fab.simphysics` time-stepped `physics-2d` simulation
  for `lot`'s own recorded `:bond-wire-diameter-um` and (ADR-2607992500,
  when present) `:specimen-*-mm` CAD-envelope fields (`design-for`
  above) and returns the actual simulated trajectory telemetry:
  `{:bond-pull-strength-actual n :sim-bond-pull-force-gf n
  :sim-peak-decel-mps2 n :ticks n :dt n :pull-rate-mps n}`.
  `:bond-pull-strength-actual` (= `:sim-bond-pull-force-gf`) is the
  SAME field `fab.simphysics/simulate`'s own docstring documents as
  derived from the actual simulated velocity/position trajectory, not
  invented -- mapped onto this actor's own pre-existing field name so
  `bond-pull-strength-out-of-range?` below needs no changes at all.
  Per `fab.simphysics/simulate`'s own disclosed GEOMETRY-INVARIANCE,
  this telemetry (unlike `fab.scene`'s render bridge) is numerically
  IDENTICAL whether or not `lot` carries real `:specimen-*-mm` fields
  -- the CAD envelope only ever changes `:anchor`'s AABB size, never
  this reading. Pure, deterministic -- no IO; the same lot fields
  always reproduce the same telemetry."
  [lot]
  (let [design (design-for lot)
        sim (simphysics/simulate design)]
    {:bond-pull-strength-actual (:sim-bond-pull-force-gf sim)
     :sim-bond-pull-force-gf (:sim-bond-pull-force-gf sim)
     :sim-peak-decel-mps2 (:sim-peak-decel-mps2 sim)
     :ticks (:ticks sim)
     :dt (:dt sim)
     :pull-rate-mps (:pull-rate-mps sim)}))

(defn bond-pull-strength-out-of-range?
  "Ground-truth check: does `lot`'s own recorded :bond-pull-strength-
  actual (as of ADR-2607152000, the REAL `fab.simphysics`-simulated
  pull-test force already on file -- see `bond-pull-telemetry-for`)
  fall outside its own recorded [:bond-pull-strength-min :bond-pull-
  strength-max] bounds? Needs no mission run or proposal inspection --
  its inputs are permanent fields already on the lot, the same two-
  sided range-check shape `automotive.registry/vehicle-emissions-out-
  of-range?` and siblings use. UNCHANGED by ADR-2607152000: only HOW
  `:bond-pull-strength-actual` gets populated changed (real simulation
  instead of a hand-set demo value); the check itself, and the
  tolerance bounds it checks against, did not."
  [{:keys [bond-pull-strength-actual bond-pull-strength-min bond-pull-strength-max]}]
  (and (number? bond-pull-strength-actual) (number? bond-pull-strength-min) (number? bond-pull-strength-max)
       (or (< bond-pull-strength-actual bond-pull-strength-min)
           (> bond-pull-strength-actual bond-pull-strength-max))))

(defn simulate-process-step
  "Run the robot wafer-probe/optical-inspection/wire-bond-pull-test
  verification mission for `lot-id` (`lot` is the full lot record,
  incl. `:bond-wire-diameter-um`). Actually runs the REAL engine:
  `bond-pull-telemetry-for` -- the actual `physics-2d`-stepped
  pull-test trajectory (`:bond-pull-strength-actual`/
  `:sim-peak-decel-mps2`).

  Returns {:mission .. :actions [{:action .. :proof ..} ..] :passed?
  bool :bond-pull-strength-actual n :sim-peak-decel-mps2 n :ticks n
  :dt n :pull-rate-mps n}. Deterministic: :passed? is derived from the
  lot's OWN recorded :bond-wire-diameter-um via the REAL simulated
  trajectory (`bond-pull-strength-out-of-range?`), never invented or
  randomized -- `kotoba.robotics` mandates no network/IO, and a
  repeatable simulation is what makes the governor's independent
  recheck (`simulation-out-of-tolerance?`) meaningful."
  [lot-id lot]
  (let [telemetry (bond-pull-telemetry-for lot)
        out-of-range? (bond-pull-strength-out-of-range? (merge lot telemetry))
        reading (if out-of-range? :out-of-tolerance :nominal)
        mission (robotics/mission (str "mission-" lot-id "-process-verify")
                                   :robot/cleanroom-process-cell-1
                                   :wafer-process-verification
                                   :boundaries {:station "cleanroom-process-verification-cell"}
                                   :max-steps (count mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :lot-id lot-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance :simulated)}))
                      mission-actions)]
    {:mission mission
     :actions actions
     :passed? (not out-of-range?)
     :bond-pull-strength-actual (:bond-pull-strength-actual telemetry)
     :sim-peak-decel-mps2 (:sim-peak-decel-mps2 telemetry)
     :ticks (:ticks telemetry)
     :dt (:dt telemetry)
     :pull-rate-mps (:pull-rate-mps telemetry)}))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does `lot`'s OWN
  current, on-file REAL `fab.simphysics`-simulated bond-pull-strength
  fields fall out of range right now? Ignores whatever :passed? verdict
  a prior mission run stored -- identical in spirit to `fab.registry/
  yield-rate-insufficient?`'s refusal to trust a proposal's self-
  report."
  [lot]
  (bond-pull-strength-out-of-range? lot))
