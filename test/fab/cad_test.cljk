(ns fab.cad-test
  "fab.cad's real BREP wire-bond test-specimen envelope bridge
  (ADR-2607992500) -- envelope-dims-mm's real-vs-default fallback
  discipline, and envelope-solid/envelope-mesh's genuine tessellation
  output. Direct port of autoparts.cad-test's assertions, adapted to
  fab's own fixed AABB constants."
  (:require [clojure.test :refer [deftest is testing]]
            [fab.cad :as cad]
            [fab.simphysics :as simphysics]))

(deftest envelope-dims-mm-falls-back-to-disclosed-defaults-when-absent
  (testing "a lot with no :specimen-*-mm fields gets the disclosed defaults"
    (is (= {:length-mm cad/default-specimen-length-mm
            :width-mm cad/default-specimen-width-mm
            :height-mm cad/default-specimen-height-mm}
           (cad/envelope-dims-mm {:id "lot-x" :bond-wire-diameter-um 25.0}))))
  (testing "nil lot also falls back cleanly"
    (is (= {:length-mm cad/default-specimen-length-mm
            :width-mm cad/default-specimen-width-mm
            :height-mm cad/default-specimen-height-mm}
           (cad/envelope-dims-mm nil)))))

(deftest envelope-dims-mm-uses-a-lots-own-real-measurement-when-present
  (testing "an explicit :specimen-*-mm triple overrides the defaults"
    (is (= {:length-mm 0.5 :width-mm 1.2 :height-mm 0.3}
           (cad/envelope-dims-mm {:specimen-length-mm 0.5
                                   :specimen-width-mm 1.2
                                   :specimen-height-mm 0.3}))))
  (testing "a partial triple only overrides the fields actually given"
    (is (= {:length-mm 0.8
            :width-mm cad/default-specimen-width-mm
            :height-mm cad/default-specimen-height-mm}
           (cad/envelope-dims-mm {:specimen-length-mm 0.8})))))

(deftest default-specimen-dims-reproduce-simphysics-prior-fixed-constants
  (testing "the disclosed fallback defaults are DEFINED to reproduce
            fab.simphysics's pre-ADR-2607992500 anchor-half-w-m/
            anchor-half-h-m figures (to within IEEE-754 double-rounding --
            2.0e-4/2000.0 is not bit-identical to the literal 1.0e-7 due to
            the mm->m/2 division path, a real floating-point property, not
            a design mismatch), so a lot with nothing on file behaves
            identically (in practice, not merely to the last bit) to this
            actor's behavior before this ADR"
    (is (< (Math/abs (- simphysics/anchor-half-w-m (/ cad/default-specimen-length-mm 2000.0))) 1e-15))
    (is (< (Math/abs (- simphysics/anchor-half-h-m (/ cad/default-specimen-width-mm 2000.0))) 1e-15))))

(deftest envelope-solid-produces-real-tessellatable-geometry
  (let [{:keys [dims] :as solid} (cad/envelope-solid {:specimen-length-mm 0.5
                                                        :specimen-width-mm 1.2
                                                        :specimen-height-mm 0.3})]
    (is (= {:length-mm 0.5 :width-mm 1.2 :height-mm 0.3} dims))
    (is (seq (:vertices solid)))
    (is (seq (:edges solid)))
    (testing "the tessellated footprint's X/Y extent matches the requested dims (mm)"
      (let [{:keys [positions]} (cad/envelope-mesh solid)
            extent (fn [axis] (- (apply max (map #(nth % axis) positions))
                                  (apply min (map #(nth % axis) positions))))]
        (is (< (Math/abs (- (extent 0) 0.5)) 1e-9))
        (is (< (Math/abs (- (extent 1) 1.2)) 1e-9))))))

(deftest envelope-mesh-is-well-formed
  (let [solid (cad/envelope-solid {:specimen-length-mm 2.0e-4
                                    :specimen-width-mm 2.0e-3
                                    :specimen-height-mm 0.125})
        {:keys [positions indices]} (cad/envelope-mesh solid)]
    (is (pos? (count positions)))
    (is (pos? (count indices)))
    (is (zero? (mod (count indices) 3)) "indices are complete triangles")
    (is (every? #(<= 0 % (dec (count positions))) indices)
        "every index references a valid vertex")
    (is (every? #(= 3 (count %)) positions) "positions are [x y z]")))

(deftest envelope-dims-mm-vary-per-lot
  (testing "two lots with different real coupon measurements get genuinely
            different envelopes -- this is not a fixed constant dressed up
            as per-lot data"
    (is (not= (cad/envelope-dims-mm {:specimen-length-mm 0.4 :specimen-width-mm 0.9})
              (cad/envelope-dims-mm {:specimen-length-mm 1.0 :specimen-width-mm 2.5})))))
