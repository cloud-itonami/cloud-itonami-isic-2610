(ns fab.upstream-ore-pedigree-test
  "ADR-2607999980's cross-actor supply-chain-linkage check
  (`fab.governor/upstream-ore-pedigree-claims-out-of-tolerance-
  violations`, direct port of ADR-2607999970's `steelworks.governor`
  equivalent), exercised with HAND-BUILT `kotoba.pedigree` records
  (via the real `kotoba.pedigree/claim` constructor -- never a raw
  map literal that merely LOOKS like a pedigree). The genuine
  cross-repo proof -- an actual call into `cloud-itonami-isic-0729`'s
  `nonferrousops.export/pedigree-for-production-record` -- lives in
  `test-cross-repo/fab/pedigree_integration_test.clj` (a separate
  alias, see deps.edn); this file only proves the GOVERNOR check
  itself is correct in isolation, independent of which upstream actor
  produced the pedigree. Mirrors `steelworks.upstream-ore-pedigree-
  test`, one link earlier in this fleet's pattern."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.pedigree :as pedigree]
            [langgraph.graph :as g]
            [fab.governor :as governor]
            [fab.store :as store]
            [fab.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :fab-engineer :phase 3})

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify! [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :requirements/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- simulate-robotics! [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-robotics") {:op :robotics/simulate-process-step :subject subject} operator)
  (approve! actor (str tid-prefix "-robotics")))

(defn- attach-ore-pedigree! [actor tid-prefix subject pedigree]
  (exec-op actor (str tid-prefix "-pedigree")
           {:op :lot/intake :subject subject
            :patch {:id subject :upstream-ore-pedigree pedigree}}
           operator))

(defn- clean-pedigree []
  (pedigree/claim "PEDIGREE-prod-1" "prod-1" "cloud-itonami-isic-0729"
                   {:grade-actual (+ governor/min-upstream-ore-grade-pct 5.0)
                    :quantity-tonnes 3600.0}
                   :evidence-basis ["nonferrousops.store/production-record"]
                   :issued-at "2026-07-16"))

(defn- weak-pedigree []
  (pedigree/claim "PEDIGREE-prod-2" "prod-2" "cloud-itonami-isic-0729"
                   {:grade-actual (- governor/min-upstream-ore-grade-pct 5.0)
                    :quantity-tonnes 800.0}
                   :evidence-basis ["nonferrousops.store/production-record"]
                   :issued-at "2026-07-16"))

(deftest absent-upstream-ore-pedigree-is-a-no-op
  (testing "a lot with no :upstream-ore-pedigree dispatches exactly as before this ADR -- no new violation"
    (let [[db actor] (fresh)
          _ (verify! actor "t1pre" "lot-1")
          _ (simulate-robotics! actor "t1pre2" "lot-1")
          res (exec-op actor "t1" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)]
      (is (nil? (:upstream-ore-pedigree (store/lot db "lot-1"))))
      (is (= :interrupted (:status res)) "still escalates for human approval, same as before -- no HARD hold introduced")
      (let [r2 (approve! actor "t1")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:process-step-dispatched? (store/lot db "lot-1"))))))))

(deftest valid-in-tolerance-upstream-ore-pedigree-dispatches-normally
  (testing "a shape-valid pedigree whose claim clears the acceptance floor does not block dispatch"
    (let [[db actor] (fresh)
          _ (verify! actor "t2pre" "lot-1")
          _ (simulate-robotics! actor "t2pre2" "lot-1")
          _ (attach-ore-pedigree! actor "t2pre3" "lot-1" (clean-pedigree))
          res (exec-op actor "t2" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)]
      (is (some? (:upstream-ore-pedigree (store/lot db "lot-1"))))
      (is (= :interrupted (:status res)) "still escalates for human approval -- actuation is never auto")
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:process-step-dispatched? (store/lot db "lot-1"))))))))

(deftest upstream-ore-pedigree-claims-out-of-tolerance-is-held
  (testing "a shape-valid pedigree whose claim falls below the acceptance floor -> HARD hold, independent of yield/robotics/evidence being otherwise clean"
    (let [[db actor] (fresh)
          _ (verify! actor "t3pre" "lot-1")
          _ (simulate-robotics! actor "t3pre2" "lot-1")
          _ (attach-ore-pedigree! actor "t3pre3" "lot-1" (weak-pedigree))
          res (exec-op actor "t3" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-ore-pedigree-claims-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest upstream-ore-pedigree-invalid-shape-is-held
  (testing "an attached map that fails kotoba.pedigree/valid? (e.g. a non-numeric claim, mimicking a self-reported string) -> HARD hold, never trusted at face value"
    (let [[db actor] (fresh)
          bad-pedigree (assoc (clean-pedigree) :pedigree/claims {:grade-actual "high"})
          _ (verify! actor "t4pre" "lot-1")
          _ (simulate-robotics! actor "t4pre2" "lot-1")
          _ (attach-ore-pedigree! actor "t4pre3" "lot-1" bad-pedigree)
          res (exec-op actor "t4" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)]
      (is (false? (pedigree/valid? bad-pedigree)) "sanity: the fixture really is shape-invalid")
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-ore-pedigree-invalid-shape} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest upstream-ore-pedigree-check-scoped-to-dispatch-process-step-op
  (testing "the check only fires for :actuation/dispatch-process-step -- an out-of-tolerance pedigree already on file does not block an unrelated op"
    (let [[_db actor] (fresh)
          _ (attach-ore-pedigree! actor "t5pre" "lot-1" (weak-pedigree))
          res (exec-op actor "t5" {:op :requirements/verify :subject "lot-1"} operator)]
      (is (= :interrupted (:status res)) "requirements/verify is unaffected by an out-of-tolerance upstream ore pedigree")
      (let [r2 (approve! actor "t5")]
        (is (= :commit (get-in r2 [:state :disposition])))))))
