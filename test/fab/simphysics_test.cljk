(ns fab.simphysics-test
  "fab.simphysics's physics-2d-backed time-stepped wire-bond pull-test
  simulation, exercised directly -- the fab analog of
  `vdesign.simphysics-test` (ADR-2607152000, generalizing
  ADR-2607151600's automotive pilot). Also covers fab.cad's real BREP
  bridge into the `:anchor` body's AABB (ADR-2607992500), including
  the fab-specific GEOMETRY-INVARIANCE finding disclosed in this ns's
  own docstring: unlike autoparts.robotics-test (where CAD geometry
  genuinely shifts :trajectory), here it does NOT -- verified below,
  not just asserted in prose."
  (:require [clojure.test :refer [deftest is testing]]
            [fab.cad :as cad]
            [fab.simphysics :as simphysics]))

(deftest trajectory-actually-evolves
  (testing "the trajectory is a real per-tick simulation output, not a
            no-op -- position and velocity both change across ticks,
            and the anchor sheds its pull-rate velocity by the last
            tick (contact + settling actually happened)"
    (let [{:keys [trajectory ticks]} (simphysics/simulate {:bond-wire-diameter-um 25.0})
          first-t (first trajectory)
          last-t (last trajectory)]
      (is (> ticks 1))
      (is (= ticks (count trajectory)))
      (is (not= (:position first-t) (:position last-t))
          "the anchor body must actually move over the simulated ticks")
      (is (not= (:velocity first-t) (:velocity last-t))
          "the anchor body's velocity must actually change (it starts
           at the pull rate and must stop on reaching the tension
           limit)")
      (is (< (Math/abs (first (:velocity last-t)))
             (* 0.5 (first (:velocity first-t))))
          "by the last tick the anchor must have shed at least half its
           pull-rate velocity (contact + settling actually happened)"))))

(deftest reference-diameter-reproduces-reference-force
  (testing "a standard 25um wire (this module's own calibration
            reference) reproduces reference-pull-force-gf exactly, by
            construction (reference-mass-kg is DERIVED from this
            relationship, not the other way around) -- a sanity check
            that the impulse-mechanics wiring (F = m*dv/dt) actually
            reproduces the calibration it claims to"
    (let [sim (simphysics/simulate {:bond-wire-diameter-um simphysics/reference-wire-diameter-um})]
      (is (< (Math/abs (- (:sim-bond-pull-force-gf sim) simphysics/reference-pull-force-gf))
             1e-9)))))

(deftest thicker-wire-shows-higher-force
  (testing "wire DIAMETER is the lever that moves
            :sim-bond-pull-force-gf in this model (cross-sectional-area
            scaling, see namespace docstring) -- a genuinely thicker
            wire must show a genuinely higher simulated pull force, and
            a genuinely thinner wire a genuinely lower one"
    (let [thin (simphysics/simulate {:bond-wire-diameter-um 15.0})
          nominal (simphysics/simulate {:bond-wire-diameter-um 25.0})
          thick (simphysics/simulate {:bond-wire-diameter-um 33.0})]
      (is (< (:sim-bond-pull-force-gf thin) (:sim-bond-pull-force-gf nominal)))
      (is (< (:sim-bond-pull-force-gf nominal) (:sim-bond-pull-force-gf thick))))))

(deftest force-scales-with-diameter-squared
  (testing "cross-sectional-area scaling: doubling the wire diameter
            must quadruple the simulated force (mass ∝ diameter^2, and
            decel is diameter-invariant -- see next test), not merely
            increase it by some arbitrary amount"
    (let [base (simphysics/simulate {:bond-wire-diameter-um 20.0})
          doubled (simphysics/simulate {:bond-wire-diameter-um 40.0})
          ratio (/ (:sim-bond-pull-force-gf doubled) (:sim-bond-pull-force-gf base))]
      (is (< (Math/abs (- ratio 4.0)) 1e-6)
          (str "expected ~4x, got " ratio)))))

(deftest diameter-alone-does-not-change-peak-decel
  (testing "documented, verified finding (namespace docstring): colliding
            with a mass-0 (immovable) tension-limit-wall, physics_2d's
            impulse resolution is independent of the moving anchor's
            own mass -- doubling bond-wire-diameter-um (and hence the
            mass-scaling abstraction) at the SAME pull-rate/travel-
            distance produces the SAME :sim-peak-decel-mps2, not a
            fabricated heavier-implies-higher relationship"
    (let [a (simphysics/simulate {:bond-wire-diameter-um 20.0})
          b (simphysics/simulate {:bond-wire-diameter-um 40.0})]
      (is (< (Math/abs (- (:sim-peak-decel-mps2 a) (:sim-peak-decel-mps2 b))) 1e-9)))))

(deftest missing-diameter-falls-back-to-reference
  (testing "an absent :bond-wire-diameter-um falls back to
            reference-wire-diameter-um, reproducing reference-pull-
            force-gf -- never throws, never silently produces NaN"
    (let [sim (simphysics/simulate {})]
      (is (< (Math/abs (- (:sim-bond-pull-force-gf sim) simphysics/reference-pull-force-gf))
             1e-9)))))

;; ----------------------- ADR-2607992500 CAD-derived geometry -----------------------

(deftest lot-with-no-specimen-fields-is-unchanged-from-pre-adr-2607992500-behavior
  (testing "a lot with only :bond-wire-diameter-um (no real coupon geometry on
            file) produces the SAME anchor AABB half-extents this ns used as
            fixed constants before this ADR (fab.cad's defaults are defined to
            reproduce them exactly, to within IEEE-754 double-rounding -- see
            fab.cad-test's own epsilon-based check of this same fact), and
            identical numeric results to an explicit-default-dims call"
    (let [bare (simphysics/simulate {:bond-wire-diameter-um 25.0})
          explicit (simphysics/simulate {:bond-wire-diameter-um 25.0
                                          :specimen-length-mm cad/default-specimen-length-mm
                                          :specimen-width-mm cad/default-specimen-width-mm})]
      (is (< (Math/abs (- simphysics/anchor-half-w-m (:half-w (:anchor-half-extents-m bare)))) 1e-15))
      (is (< (Math/abs (- simphysics/anchor-half-h-m (:half-h (:anchor-half-extents-m bare)))) 1e-15))
      (is (= (:sim-bond-pull-force-gf bare) (:sim-bond-pull-force-gf explicit)))
      (is (= (:sim-peak-decel-mps2 bare) (:sim-peak-decel-mps2 explicit)))
      (is (= (:ticks bare) (:ticks explicit)))
      (is (= (:trajectory bare) (:trajectory explicit))
          "bare and explicit compute the IDENTICAL geometry (explicit passes
           the exact same default constants through explicitly), so -- unlike
           the small-vs-large comparison below -- these two calls hit no
           floating-point tick-alignment divergence at all"))))

(deftest cad-derived-specimen-geometry-genuinely-changes-the-anchors-collider-size
  (testing "two lots with the SAME :bond-wire-diameter-um but DIFFERENT real
            :specimen-length-mm/:specimen-width-mm produce DIFFERENT
            :anchor-half-extents-m -- a genuine, non-cosmetic effect of
            fab.cad's real per-lot geometry, verified via the anchor body's
            own AABB size (see next tests for the real, verified nuance in
            how much of this shows up in :trajectory itself)"
    (let [small (simphysics/simulate {:bond-wire-diameter-um 25.0
                                       :specimen-length-mm 0.4 :specimen-width-mm 0.9})
          large (simphysics/simulate {:bond-wire-diameter-um 25.0
                                       :specimen-length-mm 1.6 :specimen-width-mm 3.6})]
      (is (not= (:anchor-half-extents-m small) (:anchor-half-extents-m large)))
      (is (= {:half-w 2.0e-4 :half-h 4.5e-4} (:anchor-half-extents-m small))
          "length-mm/2000.0, width-mm/2000.0 -- 0.4/2000.0, 0.9/2000.0")
      (is (= {:half-w 8.0e-4 :half-h 1.8e-3} (:anchor-half-extents-m large))
          "1.6/2000.0, 3.6/2000.0"))))

(deftest cad-derived-geometry-does-not-change-the-force-reading-disclosed-invariant
  (testing "simulate's own documented geometry-invariance: peak deceleration /
            bond-pull force are driven by pull rate, wire diameter (mass), and
            travel distance -- NEVER by the specimen envelope's outer
            bounding-box size -- verified here, not just asserted in prose, so
            a future change that breaks this real property of the 'boxcar'
            collision technique is caught"
    (let [small (simphysics/simulate {:bond-wire-diameter-um 25.0
                                       :specimen-length-mm 0.4 :specimen-width-mm 0.9})
          large (simphysics/simulate {:bond-wire-diameter-um 25.0
                                       :specimen-length-mm 1.6 :specimen-width-mm 3.6})]
      (is (= (:sim-peak-decel-mps2 small) (:sim-peak-decel-mps2 large)))
      (is (= (:sim-bond-pull-force-gf small) (:sim-bond-pull-force-gf large)))
      (is (= (:ticks small) (:ticks large)))
      (is (= (:dt small) (:dt large))))))

(deftest pre-collision-trajectory-is-identical-regardless-of-specimen-geometry
  (testing "the anchor's own reported :trajectory during the pure constant-
            velocity approach phase (before any collision has been detected
            for EITHER geometry) is bit-for-bit IDENTICAL regardless of
            specimen geometry -- this segment's position/velocity integration
            never reads half-w at all, only collision DETECTION does, so this
            portion is safely, algebraically geometry-invariant (verified
            here, not merely asserted) -- see ns docstring's GEOMETRY-
            INVARIANCE section"
    (let [small (simphysics/simulate {:bond-wire-diameter-um 25.0
                                       :specimen-length-mm 0.4 :specimen-width-mm 0.9})
          large (simphysics/simulate {:bond-wire-diameter-um 25.0
                                       :specimen-length-mm 1.6 :specimen-width-mm 3.6})
          ;; ticks 0..4 are guaranteed pre-collision for BOTH geometries in
          ;; this test's inputs (verified against the actual :trajectory
          ;; below, not assumed) -- see the floating-point caveat test.
          pre-collision-ticks 5]
      (is (= (take pre-collision-ticks (:trajectory small))
             (take pre-collision-ticks (:trajectory large))))
      (is (every? #(= [simphysics/pull-rate-mps 0.0] (:velocity %))
                  (take pre-collision-ticks (:trajectory small)))
          "sanity: this slice really is the pure constant-velocity approach,
           not accidentally including the collision tick"))))

(deftest post-collision-trajectory-can-diverge-by-one-tick-a-real-floating-point-caveat
  (testing "a REAL, VERIFIED finding this ns's docstring discloses (and an
            earlier draft of this test suite did NOT expect, until this
            assertion caught it): approach-gap-m is a PRE-EXISTING constant
            (4x travel-distance-m) that makes the mathematically-exact
            collision boundary land exactly on an integer tick multiple at
            this ns's defaults -- a numerically fragile knife-edge. Once
            half-w genuinely varies per lot (this ADR), IEEE-754 rounding of
            half-w-involving sums can tip which TICK first detects the
            collision, so the post-collision segment of :trajectory is NOT
            reliably bit-identical across differing specimen geometry -- see
            ns docstring for the full disclosure. What DOES still hold,
            verified: both segments converge to the SAME final resting
            position (within settle-ticks' own documented convergence), and
            :ticks/:sim-peak-decel-mps2/:sim-bond-pull-force-gf remain
            exactly invariant regardless (see the two tests above/below)"
    (let [small (simphysics/simulate {:bond-wire-diameter-um 25.0
                                       :specimen-length-mm 0.4 :specimen-width-mm 0.9})
          large (simphysics/simulate {:bond-wire-diameter-um 25.0
                                       :specimen-length-mm 1.6 :specimen-width-mm 3.6})
          final-pos (fn [r] (first (:position (last (:trajectory r)))))]
      (is (not= (:trajectory small) (:trajectory large))
          "documents the real divergence this ns's docstring discloses -- if
           this assertion ever starts failing (i.e. the two trajectories
           become identical), that is GOOD news, not a regression: it would
           mean a future engine/placement change closed this floating-point
           gap, and this test should be updated to say so")
      (is (< (Math/abs (- (final-pos small) (final-pos large))) 1e-9)
          "both geometries' anchors still settle to the SAME final resting
           position, within the positional-correction convergence
           settle-ticks documents")
      (is (= (:ticks small) (:ticks large)))
      (is (not= (:anchor-half-extents-m small) (:anchor-half-extents-m large))
          "confirms the geometry genuinely IS different -- this is a real
           finding about a real divergence, not two identical inputs"))))

(deftest wire-diameter-still-scales-force-independent-of-geometry
  (testing "mass (wire diameter) legitimately scales the force reading even
            when specimen geometry is held fixed -- the two effects (diameter
            -> force, geometry -> anchor collider size) are orthogonal, as
            documented"
    (let [thin (simphysics/simulate {:bond-wire-diameter-um 15.0
                                      :specimen-length-mm 0.8 :specimen-width-mm 1.8})
          thick (simphysics/simulate {:bond-wire-diameter-um 33.0
                                       :specimen-length-mm 0.8 :specimen-width-mm 1.8})]
      (is (< (:sim-bond-pull-force-gf thin) (:sim-bond-pull-force-gf thick)))
      (is (= (:sim-peak-decel-mps2 thin) (:sim-peak-decel-mps2 thick))
          "peak deceleration itself is diameter/mass-invariant"))))

(deftest specimen-half-extents-m-reads-fab-cads-real-per-lot-dims
  (testing "specimen-half-extents-m (public -- see its own docstring for why)
            agrees with fab.cad/envelope-dims-mm for the same lot, confirming
            the CAD bridge is genuinely wired in, not a private/parallel
            implementation"
    (let [lot {:specimen-length-mm 0.6 :specimen-width-mm 1.4}
          {:keys [length-mm width-mm]} (cad/envelope-dims-mm lot)]
      (is (= {:half-w (/ length-mm 2000.0) :half-h (/ width-mm 2000.0)}
             (simphysics/specimen-half-extents-m lot))))))
