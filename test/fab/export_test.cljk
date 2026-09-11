(ns fab.export-test
  "`fab.export/pedigree-for-lot`'s cross-actor supply-chain-linkage
  export (ADR-2607999980, direct port of ADR-2607999950's pattern to
  the smartphone chain)."
  (:require [clojure.test :refer [deftest is testing]]
            [fab.export :as export]
            [fab.robotics :as robotics]
            [kotoba.pedigree :as pedigree]))

(deftest pedigree-for-lot-builds-a-valid-pedigree-from-real-telemetry
  (testing "a lot carrying its own real, already-simulated wire-bond pull-test telemetry yields a shape-valid pedigree"
    (let [lot (merge {:id "lot-pedigree-1" :bond-wire-diameter-um 25.0}
                      (robotics/bond-pull-telemetry-for {:bond-wire-diameter-um 25.0}))
          p (export/pedigree-for-lot lot "2026-07-16")]
      (is (some? p))
      (is (true? (pedigree/valid? p)))
      (is (= "lot-pedigree-1" (:pedigree/subject-lot-id p)))
      (is (= "cloud-itonami-isic-2610" (:pedigree/issuing-actor p)))
      (is (= "2026-07-16" (:pedigree/issued-at p)))
      (is (not (contains? p :pedigree/upstream))
          "no :upstream-ore-pedigree on file -> no :pedigree/upstream key at all")
      (testing "the claim value is the lot's OWN real simulated reading, not invented"
        (is (= (:bond-pull-strength-actual lot)
               (pedigree/claim-value p :bond-pull-strength-gf))))))
  (testing "a thicker bond wire yields a proportionally different pedigree claim -- proves the claim tracks the real simulated trajectory, not a fixed number"
    (let [thin-lot (merge {:id "lot-thin"} (robotics/bond-pull-telemetry-for {:bond-wire-diameter-um 15.0}))
          thick-lot (merge {:id "lot-thick"} (robotics/bond-pull-telemetry-for {:bond-wire-diameter-um 30.0}))
          thin-p (export/pedigree-for-lot thin-lot "2026-07-16")
          thick-p (export/pedigree-for-lot thick-lot "2026-07-16")]
      (is (< (pedigree/claim-value thin-p :bond-pull-strength-gf)
             (pedigree/claim-value thick-p :bond-pull-strength-gf))))))

(deftest pedigree-for-lot-never-fabricates-missing-telemetry
  (testing "a lot with no real :bond-pull-strength-actual on file yields nil, never an invented pedigree"
    (is (nil? (export/pedigree-for-lot {:id "lot-x"} "2026-07-16")))
    (is (nil? (export/pedigree-for-lot {:id "lot-x" :bond-wire-diameter-um 25.0} "2026-07-16"))
        "bond-wire-diameter-um alone is not telemetry -- the simulation must actually have been run and merged in first")))

;; ---------------------------------------------------------------------------
;; :upstream-ore-pedigree embedding (ADR-2607999980, 2-hop chaining)
;; ---------------------------------------------------------------------------

(deftest pedigree-for-lot-embeds-a-genuine-upstream-ore-pedigree
  (testing "a lot carrying :upstream-ore-pedigree (an isic-0729 non-ferrous-ore production-record pedigree already independently re-verified by this actor's own governor) embeds it verbatim as :pedigree/upstream -- a real 2-hop chain (ore -> lot -> ...)"
    (let [ore-pedigree (pedigree/claim "PEDIGREE-prod-1" "prod-1" "cloud-itonami-isic-0729"
                                        {:grade-actual 26.5 :quantity-tonnes 3600.0}
                                        :evidence-basis ["nonferrousops.store/production-record"]
                                        :issued-at "2026-07-16")
          lot (merge {:id "lot-pedigree-2" :bond-wire-diameter-um 25.0 :upstream-ore-pedigree ore-pedigree}
                     (robotics/bond-pull-telemetry-for {:bond-wire-diameter-um 25.0}))
          p (export/pedigree-for-lot lot "2026-07-16")]
      (is (true? (pedigree/valid? p)))
      (is (= ore-pedigree (:pedigree/upstream p)))
      (testing "each hop's own claim stays independently readable -- a genuine ore -> lot chain, not a flattened summary"
        (is (= 26.5 (pedigree/claim-value (:pedigree/upstream p) :grade-actual)))
        (is (= (:bond-pull-strength-actual lot) (pedigree/claim-value p :bond-pull-strength-gf)))))))
