(ns fab.motionplan
  "Extends `fab.robotics/mission-actions` -- the 3-step wafer-probe-
  electrical-test / optical-defect-inspection-scan / wire-bond-pull-
  test robot verification mission this actor already runs on every
  lot (`fab.robotics/simulate-process-step`) -- into an actual ordered
  list of Cartesian waypoints, one per mission action, walking the
  SAME action order the real mission already commits to the audit
  ledger (ADR-2607992500 -- direct port of `autoparts.motionplan`'s
  reference pattern, ADR-2607160000, to fab's own case, itself a port
  of `kami-engine-vehicle-designer`'s `vdesign.motionplan`,
  ADR-2607151600).

  Honest scope, HONEST DESIGN CHOICE disclosed (mirrors `fab.cad` and
  `fab.simphysics`'s own disclosed choices, and `autoparts.motionplan`'s
  before them): `vdesign.motionplan` extends `vdesign.process/plan`'s
  real multi-station BOM + 4D assembly-order sequence (the giemon-
  factory `construction.order.json :seq` pattern) -- but THIS repo has
  no multi-station BOM/assembly-order system at all, and
  ADR-2607160000 (which this ADR follows) explicitly directs NOT
  inventing one just to mirror automotive's shape. Instead this ns
  reuses `fab.robotics/mission-actions`'s existing, REAL 3-step list AS
  the station sequence -- the same 3 actions `simulate-process-step`
  already runs and records, walked in the same order, never a new
  invented process model.

  This is a WAYPOINT LIST -- a plausible, honestly simplified layout
  (mission actions placed at a fixed pitch along a straight line,
  working height derived from the lot's own real packaging-envelope
  dims via `fab.cad`) -- NOT an inverse-kinematics solver, NOT a
  trajectory optimizer, and it does not drive any real robot
  controller. `:tool-orientation` is a fixed 'straight down' approach
  vector, not a solved end-effector pose.

  `:station` is each action's own `:step` keyword name (as a string):
  this actor's data model has no separate station-naming concept the
  way `vdesign.process/plan`'s multi-station BOM does (every action
  runs at/near the SAME `:robot/cleanroom-process-cell-1`, see
  `fab.robotics/simulate-process-step`), so the mission step honestly
  doubles as its own station identity rather than inventing station
  names this actor's data has never had. Spacing the 3 actions along a
  line by `station-pitch-m` is the SAME simplifying convention
  `vdesign.motionplan`/`autoparts.motionplan` use for their own multi-
  /single-station layouts, reused here even though this actor's own
  actions likely run at or near one physical cleanroom cell --
  disclosed, not hidden."
  (:require [fab.cad :as cad]
            [fab.robotics :as robotics]))

(def ^:const station-pitch-m
  "Nominal spacing between adjacent mission-action waypoints (m) -- a
  plausible, round figure, honestly NOT derived from any real
  cleanroom cell's actual layout (mirrors `autoparts.motionplan/
  station-pitch-m`, itself scaled down from automotive's 5.0 m
  assembly-line figure to a plausible single-cell scale; reused
  verbatim here at the same 1.5 m plausible single-cell scale)."
  1.5)

(def ^:const default-tool-orientation
  "Fixed straight-down tool-approach vector -- NOT a solved end-
  effector orientation (this namespace is not an IK solver; mirrors
  `autoparts.motionplan/default-tool-orientation`)."
  [0.0 0.0 -1.0])

(def ^:const default-working-height-m
  "Fallback working height (m) when `motion-plan-for` is called with no
  lot at all (mirrors `autoparts.motionplan/default-working-height-m`)."
  0.75)

(defn- working-height-m
  "Half the lot's own real tessellated specimen-envelope height
  (`fab.cad/envelope-dims-mm`) -- a plausible fixed working height for
  every action, not a per-action solved height. Falls back to
  `default-working-height-m` only when `lot` itself is nil (an
  older/hand-rolled caller with nothing to read at all); a lot with no
  real `:specimen-height-mm` still gets a real answer via `fab.cad`'s
  own disclosed default."
  [lot]
  (if lot
    (/ (:height-mm (cad/envelope-dims-mm lot)) 2000.0)
    default-working-height-m))

(defn motion-plan-for
  "Ordered Cartesian waypoint list, one per `fab.robotics/mission-
  actions` entry (same order, same `:step` names):

    [{:seq :step :station :waypoint [x y z] :tool-orientation [dx dy dz]} ...]

  x = (action-index) * `station-pitch-m`; y = 0 (line centerline); z =
  `working-height-m`. `:seq` is 1-based (first action = seq 1).
  Deterministic: the same `lot` always produces the same plan --
  `fab.robotics/mission-actions` is itself a fixed list and no
  randomness is introduced here."
  [& [lot]]
  (let [z (working-height-m lot)]
    (mapv (fn [i {:keys [step]}]
            {:seq (inc i) :step step :station (name step)
             :waypoint [(* i station-pitch-m) 0.0 z]
             :tool-orientation default-tool-orientation})
          (range (count robotics/mission-actions))
          robotics/mission-actions)))
