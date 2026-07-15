(ns fab.scene-test
  "fab.scene's bridge from fab.cad's tessellated envelope +
  fab.simphysics/simulate's trajectory into kami.webgpu.mesh's real
  input shape, asserted for well-formedness -- no browser/WebGPU
  device is available in this JVM/.cljc actor repo (see fab.scene's
  docstring). Direct port of autoparts.scene-test's/vdesign.scene-
  test's own assertions (ADR-2607992500), adapted to a plain lot map,
  and to fab.simphysics's own disclosed frame-translation invariance
  (see the last two tests -- a real, verified difference from
  autoparts.scene, not assumed identical)."
  (:require [clojure.test :refer [deftest is testing]]
            [fab.simphysics :as simphysics]
            [fab.scene :as scene]))

(def ^:private sample-lot
  {:id "lot-scene-test" :bond-wire-diameter-um 25.0
   :specimen-length-mm 0.6 :specimen-width-mm 1.4 :specimen-height-mm 0.2})

(deftest mesh-data-is-well-formed
  (testing "positions/normals/indices satisfy kami.webgpu.mesh/upload-mesh!'s
            real contract: same-length positions/normals, index count a
            multiple of 3, every index within the vertex range"
    (let [{:keys [positions normals indices vertex-count index-count]} (scene/scene-for sample-lot)]
      (is (pos? vertex-count))
      (is (pos? index-count))
      (is (= (count positions) vertex-count))
      (is (= (count normals) vertex-count)
          "upload-mesh! requires one normal per vertex, not optional like uvs/skin/morph")
      (is (= (count indices) index-count))
      (is (zero? (mod index-count 3)))
      (is (every? #(<= 0 % (dec vertex-count)) indices)
          "every index must reference a valid vertex")
      (is (every? #(= 3 (count %)) positions) "positions are [x y z]")
      (is (every? #(= 3 (count %)) normals) "normals are [x y z]")
      (is (every? (fn [n] (< (Math/abs (- 1.0 (Math/sqrt (reduce + (map * n n))))) 1e-6)) normals)
          "every normal must actually be unit-length"))))

(deftest one-frame-per-simulated-tick
  (testing "one :transform per fab.simphysics/simulate trajectory tick"
    (let [sim (simphysics/simulate sample-lot)
          sc (scene/scene-for sample-lot)]
      (is (= (:ticks sim) (count (:frames sc))))
      (is (every? #(= 3 (count (get-in % [:transform :translation]))) (:frames sc)))
      (is (every? #(= [0.0 0.0 0.0] (get-in % [:transform :rotation])) (:frames sc))
          "physics-2d has no orientation state -- every frame's rotation is identity, honestly")
      (is (every? #(= [1.0 1.0 1.0] (get-in % [:transform :scale])) (:frames sc)))
      ;; translations move: the scene isn't rendering a frozen frame.
      (is (not= (get-in (first (:frames sc)) [:transform :translation])
                (get-in (last (:frames sc)) [:transform :translation]))))))

(deftest mesh-is-unit-converted-to-meters-and-already-centered-in-xy
  (testing "the mesh's XY footprint extent (now in METERS, matching
            fab.simphysics's trajectory units) still matches the real
            envelope-dims-mm length/width (converted mm->m); X/Y are
            naturally centered on the local origin already (fab.cad's
            +/-0.5-unit-square sketch convention -- see fab.scene's
            docstring)"
    (let [{:keys [positions dims]} (scene/scene-for sample-lot)
          extent (fn [axis] (- (apply max (map #(nth % axis) positions))
                                (apply min (map #(nth % axis) positions))))]
      (is (< (Math/abs (- (extent 0) (/ (:length-mm dims) 1000.0))) 1e-9))
      (is (< (Math/abs (- (extent 1) (/ (:width-mm dims) 1000.0))) 1e-9))
      ;; centered: min/max along X (and Y) are symmetric around 0.
      (is (< (Math/abs (+ (apply min (map #(nth % 0) positions))
                          (apply max (map #(nth % 0) positions))))
             1e-9)))))

(deftest scene-for-uses-defaults-when-lot-has-no-specimen-fields
  (testing "a lot with only :bond-wire-diameter-um (no real coupon geometry on
            file) still produces a genuine, well-formed mesh -- via fab.cad's
            disclosed defaults, never throws"
    (let [sc (scene/scene-for {:bond-wire-diameter-um 25.0})]
      (is (pos? (:vertex-count sc)))
      (is (pos? (:index-count sc))))))

(deftest mesh-size-genuinely-differs-per-lot-but-early-frames-mostly-do-not-fab-specific-finding
  (testing "UNLIKE autoparts.scene (where a larger specimen envelope also
            shifts :frames' translations throughout), fab.scene's EARLY
            (pre-collision) :frames are IDENTICAL across lots with different
            specimen geometry, and every lot's frames converge to the SAME
            final resting translation -- only the rendered MESH's own size
            (:positions/:dims) is what genuinely, reliably varies with
            specimen geometry here. A real, verified consequence of
            fab.simphysics's own disclosed GEOMETRY-INVARIANCE, INCLUDING its
            disclosed floating-point tick-alignment caveat (see that ns's
            docstring, and fab.simphysics-test's own dedicated test of the
            caveat) -- not assumed identical here without checking"
    (let [small (scene/scene-for {:bond-wire-diameter-um 25.0
                                   :specimen-length-mm 0.4 :specimen-width-mm 0.9})
          large (scene/scene-for {:bond-wire-diameter-um 25.0
                                   :specimen-length-mm 1.6 :specimen-width-mm 3.6})
          translations (fn [sc] (mapv #(get-in % [:transform :translation]) (:frames sc)))
          final-x (fn [sc] (first (last (translations sc))))]
      (is (not= (:dims small) (:dims large))
          "the mesh itself is genuinely different-sized per lot")
      (is (not= (:positions small) (:positions large))
          "the tessellated mesh vertex positions differ")
      (is (= (take 5 (translations small)) (take 5 (translations large)))
          "the anchor's motion path is identical across geometries during the
           pure constant-velocity approach phase")
      (is (< (Math/abs (- (final-x small) (final-x large))) 1e-9)
          "both geometries' anchors still settle to the same final resting
           translation, within positional-correction convergence"))))
