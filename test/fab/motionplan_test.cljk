(ns fab.motionplan-test
  "fab.motionplan/motion-plan-for -- the Cartesian waypoint list built
  from fab.robotics/mission-actions's real 3-step sequence
  (ADR-2607992500). Direct port of autoparts.motionplan-test's
  assertions, adapted to fab's own mission-actions/lot shape."
  (:require [clojure.test :refer [deftest is testing]]
            [fab.cad :as cad]
            [fab.motionplan :as motionplan]
            [fab.robotics :as robotics]))

(deftest one-waypoint-per-mission-action-same-order
  (let [plan (motionplan/motion-plan-for {:bond-wire-diameter-um 25.0})]
    (is (= (count robotics/mission-actions) (count plan)))
    (is (= (mapv :step robotics/mission-actions) (mapv :step plan)))
    (is (= [1 2 3] (mapv :seq plan)))
    (is (= ["wafer-probe-electrical-test" "optical-defect-inspection-scan" "wire-bond-pull-test"]
           (mapv :station plan)))))

(deftest waypoints-are-spaced-along-the-travel-axis
  (let [plan (motionplan/motion-plan-for {:bond-wire-diameter-um 25.0})
        xs (mapv #(first (:waypoint %)) plan)]
    (is (= [0.0 motionplan/station-pitch-m (* 2 motionplan/station-pitch-m)] xs))
    (is (every? #(= motionplan/default-tool-orientation (:tool-orientation %)) plan))
    (is (every? #(zero? (second (:waypoint %))) plan) "y is the line centerline")))

(deftest working-height-derives-from-the-lots-real-envelope
  (testing "z (working height) is half the lot's own real envelope height"
    (let [lot {:bond-wire-diameter-um 25.0 :specimen-height-mm 0.4}
          plan (motionplan/motion-plan-for lot)
          z (nth (:waypoint (first plan)) 2)]
      (is (= (/ 0.4 2000.0) z))))
  (testing "a lot with no real :specimen-height-mm still gets a real answer
            via fab.cad's own disclosed default, not motionplan's separate
            fallback"
    (let [plan (motionplan/motion-plan-for {:bond-wire-diameter-um 25.0})
          z (nth (:waypoint (first plan)) 2)]
      (is (= (/ cad/default-specimen-height-mm 2000.0) z))))
  (testing "no lot at all (older/hand-rolled caller) -> motionplan's own default-working-height-m"
    (let [plan (motionplan/motion-plan-for)
          z (nth (:waypoint (first plan)) 2)]
      (is (= motionplan/default-working-height-m z)))))

(deftest deterministic-same-lot-same-plan
  (is (= (motionplan/motion-plan-for {:bond-wire-diameter-um 25.0 :specimen-height-mm 0.4})
         (motionplan/motion-plan-for {:bond-wire-diameter-um 25.0 :specimen-height-mm 0.4}))))
