(ns fab.simphysics-test
  "fab.simphysics's physics-2d-backed time-stepped wire-bond pull-test
  simulation, exercised directly -- the fab analog of
  `vdesign.simphysics-test` (ADR-2607152000, generalizing
  ADR-2607151600's automotive pilot)."
  (:require [clojure.test :refer [deftest is testing]]
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
