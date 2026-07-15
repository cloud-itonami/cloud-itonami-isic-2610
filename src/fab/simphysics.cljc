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

  ADR-2607992500 EXTENDS this ns with a real CAD/BREP bridge, closing
  the gap this ns's docstring used to disclose ('no CAD/BREP
  pipeline'): the `:anchor` body's AABB half-extents are now
  genuinely derived from `fab.cad/envelope-dims-mm`'s tessellated
  wire-bond test-specimen envelope dims for THIS lot (mirroring
  `autoparts.robotics/specimen-half-extents-m`'s own read of
  `autoparts.cad`, itself a port of `vdesign.simphysics/vehicle-half-
  extents-m`'s read of `vdesign.cad`), instead of being bare fixed
  constants. `:tension-limit-wall` remains a FIXED test-rig constant,
  unchanged -- mirroring how `autoparts.robotics`/`vdesign.simphysics`
  only ever derive the MOVING body from CAD and leave the static
  boundary fixed; `:tension-limit-wall` in particular has no physical
  counterpart at all (see its own docstring below), so there is
  nothing real for CAD to size it against.

  GEOMETRY-INVARIANCE, verified and disclosed (this vertical's own
  real property, checked ALGEBRAICALLY AND WITH A TEST -- the actual
  finding differs in a real, disclosed way from a naive port of
  automotive's/autoparts's invariant, see below; do not assume the
  two verticals' physics/geometry coupling is identical without
  checking, per ADR-2607992500): `wall-x` below is deliberately
  computed as `anchor-x0 + half-w + approach-m + wall-half-w-m` --
  i.e. the face-to-face gap the anchor must close (from its own front
  face at start to the wall's near face) is ALWAYS exactly
  `approach-m` (= `approach-gap-m` + travel distance) in EXACT
  (real-number) arithmetic, regardless of `half-w` (it cancels out of
  the placement algebra by construction, the SAME technique
  `autoparts.robotics`'s `jaw-x0`/`limit-boundary-x` use).
  Consequently `:sim-peak-decel-mps2`/`:sim-bond-pull-force-gf`/
  `:ticks` (the total tick COUNT)/`:dt` are IDENTICAL whether `design`
  carries real `:specimen-*-mm` dims or falls back to defaults --
  VERIFIED, not merely algebraic, in `simphysics_test.clj` -- same
  shape as automotive/autoparts's disclosed invariant.

  UNLIKE automotive/autoparts, `anchor-x0` here is a FIXED coordinate-
  origin start point (`0.0`, the bond-pad reference this ns's own
  docstring already calls 'only the coordinate origin the anchor's
  travel is measured from, not a rigid body'), never offset by
  `half-w` the way `autoparts.robotics`'s `jaw-x0` is offset to sit
  flush against its own static `:fixture` body's face. So the anchor's
  REPORTED `:trajectory` positions during the pure constant-velocity
  approach phase (before any collision has been detected) are
  IDENTICAL regardless of specimen geometry too -- verified in
  `simphysics_test.clj` over the ticks both a small and a large test
  geometry are still guaranteed to be pre-collision.

  A REAL, VERIFIED CAVEAT this ns's own test suite actually caught
  (checked, not assumed -- exactly the kind of finding a naive port of
  automotive/autoparts's invariant would have silently missed):
  `approach-gap-m` = `4.0 * travel-distance-m` is a PRE-EXISTING
  (pre-ADR-2607992500) constant choice that makes `approach-m` (the
  mathematically-exact collision boundary) land EXACTLY on an integer
  multiple of the tick step `v0*dt` (5 ticks, by construction) at
  this ns's default `pull-rate-mps`/`travel-distance-m`. That is a
  numerically fragile knife-edge: once `half-w` genuinely varies
  per-lot (this ADR), floating-point rounoff in the `half-w`-involving
  face-position sums can tip the ACTUAL simulated collision detection
  to the tick before or after the mathematically-exact boundary,
  depending on the specific `half-w` value -- a real IEEE-754
  double-rounding effect, not a modeling error. The PRACTICAL
  consequence, verified in `simphysics_test.clj`: the post-collision
  settling segment of `:trajectory` (`:position` values from whichever
  tick first shows a zeroed velocity onward) CAN genuinely differ
  (by up to one tick's offset) between two lots with different real
  `:specimen-*-mm` geometry, even though both segments converge to
  the SAME final resting position (within the `settle-ticks` positional-
  correction convergence this ns's own `settle-ticks` docstring already
  documents) and `:ticks`/`:sim-peak-decel-mps2`/`:sim-bond-pull-force-
  gf` remain exactly, bit-for-bit invariant regardless. This is a
  WEAKER trajectory-invariance guarantee than a naive reading of the
  placement algebra alone would suggest (and weaker than what this
  docstring claimed in an earlier draft, before the test above caught
  it) -- disclosed honestly here, not smoothed over. `simulate`'s
  returned `:anchor-half-extents-m` exposes the REAL per-run half-
  extents used, so a caller/test can always confirm CAD geometry is
  genuinely being read even when it happens not to be visible in a
  particular slice of `:trajectory`.

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
    disclosed for its own decel-g/mass relationship.
  - GEOMETRY, similarly, moves neither `:sim-peak-decel-mps2` nor
    `:sim-bond-pull-force-gf` -- see the ns-docstring's
    GEOMETRY-INVARIANCE section above for the full, verified
    derivation (and for the further, fab-specific finding that
    geometry does not move `:trajectory` either, unlike automotive/
    autoparts)."
  (:require [fab.cad :as cad]
            [physics-2d :as p2d]))

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
  "Anchor AABB half-width along the pull axis (m) -- ADR-2607992500: no
  longer read directly by `simulate` (superseded by `fab.cad`-derived
  per-lot dims, see `specimen-half-extents-m` below), retained as a
  disclosed reference figure -- `fab.cad/default-specimen-length-mm`
  is DELIBERATELY defined to reproduce this exact half-width (2.0e-4 mm
  full length / 2 = 1.0e-7 m) when a lot carries no real
  `:specimen-length-mm`, so a lot with nothing on file gets the SAME
  anchor size this ns used before this ADR. Originally: a negligibly
  small collider (the wire/hook's own footprint is not the modeled
  quantity; only its trajectory/mass is) -- still true of the default,
  a lot with a real `:specimen-length-mm` on file now gets a genuinely
  per-lot collider size instead."
  1.0e-7)

(def ^:const anchor-half-h-m
  "Anchor AABB half-height (m), lateral -- see `anchor-half-w-m`;
  `fab.cad/default-specimen-width-mm` reproduces this exact figure
  (2.0e-3 mm full width / 2 = 1.0e-6 m)."
  1.0e-6)

(def ^:const wall-half-w-m
  "Tension-limit-wall AABB half-width along the pull axis (m) -- a
  thin, fixed virtual boundary, not a modeled second body of the real
  test rig. ADR-2607992500: stays FIXED, never CAD-derived -- this
  body has no physical counterpart at all (a pure math device standing
  in for the wire running out of compliant travel, see ns docstring),
  so there is nothing real for CAD to size it against -- mirrors
  `autoparts.robotics/limit-boundary-half-w-m`."
  1.0e-7)

(def ^:const wall-half-h-m
  "Tension-limit-wall AABB half-height (m), lateral -- wide enough
  that the anchor's travel always overlaps it head-on; no lateral
  offset is modeled. ADR-2607992500: stays FIXED, same reasoning as
  `wall-half-w-m`."
  1.0e-3)

(def ^:const settle-ticks
  "Extra ticks appended after the anchor is expected to reach the
  tension-limit wall, so the trajectory also captures post-contact
  settling -- see `vdesign.simphysics`'s own `settle-ticks` for the
  identical positional-correction-convergence rationale (`0.2^settle-
  ticks` residual overlap)."
  15)

(defn specimen-half-extents-m
  "AABB half-extents (m) for the `:anchor` body, from `fab.cad/
  envelope-dims-mm`'s REAL tessellated dims (mm) for `design` --
  travel-axis half-width = length/2, lateral half-height = width/2.
  Direct port of `autoparts.robotics/specimen-half-extents-m`'s (and,
  before it, `vdesign.simphysics/vehicle-half-extents-m`'s)
  length/width-only reading of the CAD envelope. `envelope-dims-mm`
  always returns SOME dims (a lot's own real `:specimen-*-mm` fields
  when present, `fab.cad`'s disclosed fixture-scale defaults when
  absent -- see that ns's docstring), so this always succeeds; it is
  the INPUT (whether `design` carries real specimen measurements) that
  varies, not this function's availability. PUBLIC (unlike its
  automotive counterpart, which is private): `fab.simphysics`'s own
  placement algebra makes CAD geometry invisible in `simulate`'s
  pre-collision `:trajectory` segment, and (per the ns docstring's
  disclosed floating-point caveat) not always cleanly visible in the
  post-collision segment either, so this fn is the direct, honest way
  a test/caller can verify CAD dims are genuinely being read here."
  [design]
  (let [{:keys [length-mm width-mm]} (cad/envelope-dims-mm design)]
    {:half-w (/ length-mm 2000.0)
     :half-h (/ width-mm 2000.0)}))

(defn simulate
  "Time-steps a `physics_2d` world for a wire-bond pull test on a wire
  of `bond-wire-diameter-um` (from `design`, defaulting to
  `reference-wire-diameter-um` if absent) and returns:

    {:trajectory [{:tick :position :velocity} ...]   ; anchor body only
     :sim-bond-pull-force-gf n :sim-peak-decel-mps2 n
     :ticks n :dt n :pull-rate-mps n :anchor-half-extents-m {:half-w n :half-h n}}

  `design` may also carry a real `:specimen-length-mm`/
  `:specimen-width-mm`/`:specimen-height-mm` coupon-envelope
  measurement (ADR-2607992500) -- when present, the `:anchor` body's
  AABB is sized via `specimen-half-extents-m` (`fab.cad`-derived);
  when absent, the SAME fixed defaults this ns used before this ADR.
  `:tension-limit-wall` always uses its own fixed constants (see those
  defs' docstrings for why). `:anchor-half-extents-m` in the return
  value is the REAL half-extents this run actually used, so a caller
  can confirm CAD geometry was genuinely read even in the (real,
  documented) case where it is not visible in the returned
  `:trajectory`'s post-collision segment -- see ns docstring's
  GEOMETRY-INVARIANCE section for the full, verified derivation and
  its disclosed floating-point caveat.

  opts (all optional, for tuning/testing):
    :pull-mps   override the pull rate, m/s (default `pull-rate-mps`)
    :travel-m   override the travel distance to the tension-limit
                wall, m (default `travel-distance-m`)

  `:sim-peak-decel-mps2` is the PEAK magnitude of tick-to-tick velocity
  change (along the pull axis) divided by `dt` -- derived from the
  actual simulated velocity trajectory, not invented.
  `:sim-bond-pull-force-gf` is that peak deceleration times the wire's
  own diameter-scaled mass abstraction, converted to gram-force -- see
  namespace docstring for the full, disclosed derivation (incl. the
  GEOMETRY-INVARIANCE section: neither figure moves with specimen
  geometry)."
  [{:keys [bond-wire-diameter-um] :as design} & [{:keys [pull-mps travel-m]}]]
  (let [v0 (double (or pull-mps pull-rate-mps))
        d (double (or travel-m travel-distance-m))
        dt (/ d v0)
        mass (mass-analog-kg (or bond-wire-diameter-um reference-wire-diameter-um))
        {:keys [half-w half-h] :as half-extents} (specimen-half-extents-m design)
        anchor-x0 0.0
        approach-m (+ approach-gap-m d)
        ;; wall-x is offset by half-w (and wall-half-w-m) so the
        ;; face-to-face gap the anchor must close is ALWAYS exactly
        ;; approach-m, regardless of half-w -- see ns docstring's
        ;; GEOMETRY-INVARIANCE section (same technique autoparts.
        ;; robotics's limit-boundary-x uses).
        wall-x (+ anchor-x0 half-w approach-m wall-half-w-m)
        ticks (long (+ settle-ticks (long (Math/ceil (/ approach-m (* v0 dt))))))
        anchor (p2d/make-body {:position [anchor-x0 0.0]
                                :velocity [v0 0.0]
                                :mass mass
                                :restitution 0.0
                                :friction 0.0
                                :collider (p2d/make-aabb-collider half-w half-h)
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
     :pull-rate-mps v0
     :anchor-half-extents-m half-extents}))
