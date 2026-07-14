(ns fab.simphysics
  "Time-stepped rigid-body simulation of a wire-bond destructive PULL
  test (MIL-STD-883 Method 2011-style bond-strength test), built
  directly on `kotoba-lang/physics-2d`'s real, tested impulse-based
  `world-step` solver (ADR-2607152000, generalizing ADR-2607151600's
  automotive pilot -- `vdesign.simphysics` in `kami-engine-vehicle-
  designer` -- to this vertical). Unlike automotive, fab has no
  sibling `kami-engine-*` design repo to route through, so this module
  lives DIRECTLY inside this actor's own repo (ADR-2607152000 context)
  and takes a real git-coordinate dependency on `kotoba-lang/physics-
  2d` alone (see `deps.edn`).

  HONEST modeling note -- this is a PULL (tension) test, but
  `physics_2d/world-step` only ever resolves COLLISIONS (bodies
  CLOSING on each other, per `resolve-contact`'s `vel-along <= 0`
  branch); it has no spring/tension constraint at all. So rather than
  simulate the pull directly (which this engine cannot do), this
  simulates the INVERSE, physically-equivalent event: the wire's free
  end (the `anchor`) travels at the pull-tester's own controlled
  crosshead rate until it reaches the wire's own real compliant-travel
  limit (`travel-distance-m`, standing in for the point the bond
  fails/the wire runs out of slack) -- modeled as a real AABB
  COLLISION against a static `tension-limit-wall` body positioned
  exactly there. `physics_2d`'s impulse resolver then does exactly
  what a real destructive pull-test recording DOES at the moment of
  failure: it fully absorbs the anchor's closing velocity in one
  discrete tick (restitution 0, an instantaneous 'boxcar' stop, the
  SAME simplification `vdesign.simphysics` disclosed for automotive's
  crash-stop) -- the resulting peak impulse/force, read directly off
  the ACTUAL simulated velocity change, is this ns's real
  `:sim-bond-pull-force-gf` reading. This is a reinterpretation of the
  crash-into-barrier shape (reaching-end-of-tether, not crashing into
  a barrier), not a new physics model -- disclosed here, not hidden.

  What IS real: the anchor and the tension-limit-wall are actual
  `physics_2d` `Body2D`/AABB `Collider2D` entities; `physics_2d/world-
  step` actually integrates velocity/position and actually runs its
  brute-force collision detection + impulse resolution + positional
  correction over N discrete ticks -- `:trajectory` below is the
  ACTUAL per-tick output of that solver, read back tick by tick, never
  synthesized after the fact. The bond-pad itself (where the wire is
  physically anchored to the die) is NOT modeled as a third
  `physics_2d` body -- it never participates in any collision (the
  anchor moves monotonically AWAY from it, per the pull-test's own
  physical direction), so it is only the coordinate origin the
  anchor's travel is measured from, not a rigid body in this
  simulation; the one real, simulated collision event is anchor-vs-
  tension-limit-wall.

  Deliberate modeling simplifications (disclosed, not hidden):

  - 2D projection only (`physics_2d` has no 3D solver) -- x is the
    pull direction, y is unused/lateral; world gravity is [0 0] (a
    bond-pad-plane projection of the pull, not the vertical plane a
    real pull-tester's hook geometry actually uses).
  - `pull-rate-mps`/`travel-distance-m` are DISCLOSED, REPRESENTATIVE
    test-rig constants, not independently-verified metrology
    citations: `pull-rate-mps` (0.5 mm/s) is a slow, controlled
    crosshead speed representative of commercial wire-bond pull
    testers performing a MIL-STD-883 Method 2011-style destructive
    bond-pull test (real testers deliberately run this test at a slow,
    quasi-static rate, not an impact rate); `travel-distance-m`
    (125 μm) is a representative bond-wire loop-height figure (a real,
    standard, controllable wire-bonding process geometry parameter --
    commonly-used standard loop-height profiles fall in roughly this
    range), held FIXED across every lot here (a test-rig/package
    geometry constant, not a per-lot design field).
  - `dt` is derived from THIS module's own fixed `travel-distance-m` /
    `pull-rate-mps` (the nominal transit time across the wire's own
    compliant travel) -- the SAME 'a principled, not arbitrary, choice
    that couples the model through a shared physical assumption' `dt`-
    derivation `vdesign.simphysics` used for automotive's crush-
    length/impact-speed, applied here to the wire's own travel
    distance/pull rate instead.
  - the anchor's `physics_2d` `:mass` is NOT the wire's literal mass (a
    real bonding wire is sub-milligram) -- `physics_2d` is unit-
    agnostic (its own docstring: 'no real units') and has no notion of
    material tensile strength at all, so `:mass` here is a SCALING
    ABSTRACTION: it scales with the lot's own recorded
    `:bond-wire-diameter-um` SQUARED (cross-sectional-area scaling),
    mirroring the well-established, citable engineering fact that a
    bonding wire's real breaking/pull capacity scales with its
    cross-sectional area (∝ diameter²) -- calibrated
    (`reference-mass-kg`) so that, combined with the fixed pull-rate/
    travel-distance constants, the resulting impulse-derived force for
    a standard 25 μm wire lands at `reference-pull-force-gf` (9.0 gf),
    the SAME figure this actor's own demo fixture used, pre-
    ADR-2607152000, as its then-hand-set nominal
    `:bond-pull-strength-actual` -- i.e. calibrated to reproduce this
    actor's own prior nominal reading, not an invented number, and
    checked against this actor's own EXISTING, already-established
    [:bond-pull-strength-min :bond-pull-strength-max] = [6.0 12.0] gf
    band (`fab.store/demo-data`), never a new tolerance invented for
    this ADR.
  - what is REAL and not invented: the actual `physics_2d/world-step`
    tick-by-tick impulse resolution (peak force = mass × Δv / dt,
    read directly off the simulated trajectory) that derives the
    reading, and the diameter² SCALING relationship between lots --
    NOT the absolute constants' independent metrological certification.
  - as in `vdesign.simphysics`'s crash model, MASS cancels out of the
    kinematic deceleration (colliding with the immovable, mass-0
    `tension-limit-wall`, `physics_2d`'s impulse resolution makes the
    anchor's own velocity change independent of its own mass -- see
    `resolve-contact`); only `pull-rate-mps`/`travel-distance-m` move
    `:sim-peak-decel-mps2`. The wire-diameter signal instead moves the
    FORCE reading (peak decel × mass), the same 'a genuine, verified
    property of the model, not a physics_2d-only limitation' automotive
    disclosed for its own decel-g/mass relationship."
  (:require [physics-2d :as p2d]))

(def ^:const pull-rate-mps
  "Representative wire-bond pull-tester crosshead speed (m/s) -- see
  namespace docstring's disclosure on this figure."
  5.0e-4)

(def ^:const travel-distance-m
  "Representative bond-wire loop-height / compliant-travel distance
  (m) before the simulated tension-limit collision -- see namespace
  docstring's disclosure on this figure. FIXED across every lot (a
  test-rig/package geometry constant, not a per-lot field)."
  1.25e-4)

(def ^:const approach-gap-m
  "Extra pre-contact travel distance (m), on top of `travel-distance-
  m`, so the simulated trajectory captures several real ticks of pure
  constant-velocity approach before the collision tick -- the SAME
  reason `vdesign.simphysics`'s own `default-gap-m` exists ('so the
  trajectory captures a real pre-contact approach phase, not just the
  collision tick itself'). Does not change the derived force reading
  at all: velocity is constant (no forces) during pure approach, so
  the peak tick-to-tick velocity change is unaffected by how many
  approach ticks precede the stopping collision."
  (* 4.0 travel-distance-m))

(def ^:const reference-wire-diameter-um
  "The standard gold bonding-wire diameter (μm) this module's mass-
  scaling constant is calibrated against -- 25 μm is a commonly-used
  standard fine-wire bonding diameter."
  25.0)

(def ^:const reference-pull-force-gf
  "Reference simulated pull force (gf) this module is calibrated to
  reproduce for a standard 25 μm wire at the pull-rate/travel-distance
  constants above -- 9.0 gf is the SAME figure this actor's demo
  fixture used, pre-ADR-2607152000, as its then hand-set nominal
  `:bond-pull-strength-actual` (see namespace docstring) -- chosen to
  land centrally within this actor's own EXISTING
  [:bond-pull-strength-min :bond-pull-strength-max] = [6.0 12.0] gf
  band, not an invented number."
  9.0)

(def ^:const newtons-per-gf
  "1 gram-force in newtons (standard gravity, 9.80665 m/s^2 * 1 g)."
  0.00980665)

(def ^:private nominal-decel-mps2
  "The kinematic deceleration (m/s^2) `physics_2d`'s boxcar single-tick
  stop produces at `pull-rate-mps`/`travel-distance-m`, INDEPENDENT of
  mass (see namespace docstring) -- `peak-decel = pull-rate-mps / dt`
  where `dt = travel-distance-m / pull-rate-mps`, i.e.
  `pull-rate-mps^2 / travel-distance-m`."
  (/ (* pull-rate-mps pull-rate-mps) travel-distance-m))

(def ^:const reference-mass-kg
  "The anchor's `physics_2d` mass-scaling-abstraction value (see
  namespace docstring) for a standard 25 μm wire -- DERIVED (not a
  magic literal) from `reference-pull-force-gf` and
  `nominal-decel-mps2` so that `force = mass * decel` reproduces
  `reference-pull-force-gf` exactly for the reference diameter."
  (/ (* reference-pull-force-gf newtons-per-gf) nominal-decel-mps2))

(defn- mass-analog-kg
  "The anchor's physics_2d mass-scaling abstraction for a wire of
  `diameter-um` -- scales with cross-sectional area (∝ diameter²) off
  `reference-mass-kg`/`reference-wire-diameter-um` -- see namespace
  docstring."
  [diameter-um]
  (let [r (/ (double diameter-um) reference-wire-diameter-um)]
    (* reference-mass-kg r r)))

(def ^:const anchor-half-w-m
  "Anchor AABB half-width along the pull axis (m) -- a negligibly
  small collider (the wire/hook's own footprint is not the modeled
  quantity; only its trajectory/mass is)."
  1.0e-7)

(def ^:const anchor-half-h-m
  "Anchor AABB half-height (m), lateral -- negligibly small, matching
  `anchor-half-w-m`."
  1.0e-6)

(def ^:const wall-half-w-m
  "Tension-limit-wall AABB half-width along the pull axis (m) -- a
  thin, fixed virtual boundary, not a modeled second body of the real
  test rig."
  1.0e-7)

(def ^:const wall-half-h-m
  "Tension-limit-wall AABB half-height (m), lateral -- wide enough
  that the anchor's travel always overlaps it head-on; no lateral
  offset is modeled."
  1.0e-3)

(def ^:const settle-ticks
  "Extra ticks appended after the anchor is expected to reach the
  tension-limit wall, so the trajectory also captures post-contact
  settling -- see `vdesign.simphysics`'s own `settle-ticks` for the
  identical positional-correction-convergence rationale (`0.2^settle-
  ticks` residual overlap)."
  15)

(defn simulate
  "Time-steps a `physics_2d` world for a wire-bond pull test on a wire
  of `bond-wire-diameter-um` (from `design`, defaulting to
  `reference-wire-diameter-um` if absent) and returns:

    {:trajectory [{:tick :position :velocity} ...]   ; anchor body only
     :sim-bond-pull-force-gf n :sim-peak-decel-mps2 n
     :ticks n :dt n :pull-rate-mps n}

  opts (all optional, for tuning/testing):
    :pull-mps   override the pull rate, m/s (default `pull-rate-mps`)
    :travel-m   override the travel distance to the tension-limit
                wall, m (default `travel-distance-m`)

  `:sim-peak-decel-mps2` is the PEAK magnitude of tick-to-tick velocity
  change (along the pull axis) divided by `dt` -- derived from the
  actual simulated velocity trajectory, not invented.
  `:sim-bond-pull-force-gf` is that peak deceleration times the wire's
  own diameter-scaled mass abstraction, converted to gram-force -- see
  namespace docstring for the full, disclosed derivation."
  [{:keys [bond-wire-diameter-um]} & [{:keys [pull-mps travel-m]}]]
  (let [v0 (double (or pull-mps pull-rate-mps))
        d (double (or travel-m travel-distance-m))
        dt (/ d v0)
        mass (mass-analog-kg (or bond-wire-diameter-um reference-wire-diameter-um))
        anchor-x0 0.0
        approach-m (+ approach-gap-m d)
        wall-x approach-m
        ticks (long (+ settle-ticks (long (Math/ceil (/ approach-m (* v0 dt))))))
        anchor (p2d/make-body {:position [anchor-x0 0.0]
                                :velocity [v0 0.0]
                                :mass mass
                                :restitution 0.0
                                :friction 0.0
                                :collider (p2d/make-aabb-collider anchor-half-w-m anchor-half-h-m)
                                :user-data :anchor})
        wall (p2d/make-body {:position [wall-x 0.0]
                              :velocity [0.0 0.0]
                              :mass 0.0
                              :restitution 0.0
                              :friction 0.0
                              :collider (p2d/make-aabb-collider wall-half-w-m wall-half-h-m)
                              :user-data :tension-limit-wall})
        w0 (p2d/world-new [0.0 0.0])
        [w1 aid] (p2d/world-add w0 anchor)
        [w2 _wid] (p2d/world-add w1 wall)
        worlds (reductions (fn [w _] (p2d/world-step w dt)) w2 (range ticks))
        trajectory (mapv (fn [tick world]
                            (let [b (nth (:bodies world) aid)]
                              {:tick tick :position (:position b) :velocity (:velocity b)}))
                          (range (count worlds)) worlds)
        vxs (mapv (comp first :velocity) trajectory)
        peak-decel-mps2 (->> (map (fn [va vb] (Math/abs (/ (- vb va) dt))) vxs (rest vxs))
                              (reduce max 0.0))
        force-n (* mass peak-decel-mps2)
        force-gf (/ force-n newtons-per-gf)]
    {:trajectory trajectory
     :sim-bond-pull-force-gf force-gf
     :sim-peak-decel-mps2 peak-decel-mps2
     :ticks (count trajectory)
     :dt dt
     :pull-rate-mps v0}))
