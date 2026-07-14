(ns fab.robotics
  "Robot-executed cleanroom process-step verification -- the concrete,
  actor-level realization of ADR-2607011000's robotics premise (every
  cloud-itonami vertical is designed on the premise that a robot
  performs the physical-domain work; an independent governor gates any
  action before it ever reaches hardware) for THIS actor's own
  `fab.facts` requirement that a process-step-dispatch proposal cite an
  EDA-CAE-simulation-record actually on file -- not merely a self-
  reported checklist string.

  A robot mission (`kotoba.robotics/mission`) walks the lot through
  three :sense/:actuate steps -- automated wafer-probe electrical
  test, robotic optical-defect inspection scan, automated wire-bond
  pull-test -- built with `kotoba.robotics/action` + `kotoba.robotics/
  telemetry-proof`, and reports an overall :passed? verdict.
  `simulation-out-of-tolerance?` independently re-derives that verdict
  from the lot's OWN recorded bond-pull-strength fields, never from
  the mission's self-reported result -- the SAME 'ground truth, not
  self-report' discipline `fab.registry/yield-rate-insufficient?`
  established for yield rate (this fleet's ratio-based check family;
  see that ns's docstring for prior siblings -- this ns's `bond-pull-
  strength-out-of-range?` is instead a two-sided range check, the
  SAME shape `automotive.registry/vehicle-emissions-out-of-range?`/
  `automotive.robotics/structural-tolerance-out-of-range?` and their
  siblings use, applied here to a lot's own measured wire-bond pull-
  strength against its own recorded tolerance bounds). `fab.governor`'s
  `robotics-simulation-violations` calls this ns's independent
  recheck, never the stored :passed? value, before any `:actuation/
  dispatch-process-step` proposal may commit.

  Pure data + pure functions -- no real robot I/O, no network.
  `kotoba.robotics` is itself \"policy, not control\"; this namespace
  simulates what a real robot cell would report, deterministically,
  from the lot's own recorded fields, so tests and the demo run
  offline exactly like every other sibling namespace in this actor."
  (:require [kotoba.robotics :as robotics]))

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

(defn bond-pull-strength-out-of-range?
  "Ground-truth check: does `lot`'s own recorded :bond-pull-strength-
  actual fall outside its own recorded [:bond-pull-strength-min :bond-
  pull-strength-max] bounds? Needs no mission run or proposal
  inspection -- its inputs are permanent fields already on the lot, the
  same two-sided range-check shape `automotive.registry/vehicle-
  emissions-out-of-range?` and siblings use."
  [{:keys [bond-pull-strength-actual bond-pull-strength-min bond-pull-strength-max]}]
  (and (number? bond-pull-strength-actual) (number? bond-pull-strength-min) (number? bond-pull-strength-max)
       (or (< bond-pull-strength-actual bond-pull-strength-min)
           (> bond-pull-strength-actual bond-pull-strength-max))))

(defn simulate-process-step
  "Run the robot wafer-probe/optical-inspection/wire-bond-pull-test
  verification mission for `lot-id` (`lot` is the full lot record,
  incl. bond-pull-strength-* fields). Returns {:mission .. :actions
  [{:action .. :proof ..} ..] :passed? bool}. Deterministic: :passed?
  is derived from the lot's OWN recorded bond-pull-strength fields via
  `bond-pull-strength-out-of-range?`, never invented or randomized --
  `kotoba.robotics` mandates no network/IO, and a repeatable
  simulation is what makes the governor's independent recheck
  (`simulation-out-of-tolerance?`) meaningful."
  [lot-id lot]
  (let [out-of-range? (bond-pull-strength-out-of-range? lot)
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
     :passed? (not out-of-range?)}))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does `lot`'s OWN
  current bond-pull-strength fields fall out of range right now?
  Ignores whatever :passed? verdict a prior mission run stored --
  identical in spirit to `fab.registry/yield-rate-insufficient?`'s
  refusal to trust a proposal's self-report."
  [lot]
  (bond-pull-strength-out-of-range? lot))
