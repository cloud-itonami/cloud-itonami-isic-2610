(ns fab.pedigree-integration-test
  "ADR-2607999980's critical end-to-end proof: a GENUINE cross-repo
  call into `cloud-itonami-isic-0729`'s OWN `nonferrousops.store`/
  `nonferrousops.export` -- never a hand-written EDN literal that
  merely mimics what those functions would produce. `real-upstream-
  ore-pedigree` below actually writes a production record into a real
  `nonferrousops.store` MemStore, actually reads it back out, and
  calls `nonferrousops.export/pedigree-for-production-record` (both
  required from `cloud-itonami-isic-0729`'s own source, via this
  repo's `:cross-repo-test` alias -- see deps.edn) to produce the ore
  pedigree this actor's governor then independently re-verifies, and
  (the genuine 2-hop proof) that `fab.export/pedigree-for-lot` then
  embeds as its OWN pedigree's `:pedigree/upstream`. Direct port of
  ADR-2607999970's `steelworks.pedigree-integration-test`.

  Run with `clojure -M:dev:cross-repo-test` -- kept OUT of the default
  `:test` alias (this file lives in `test-cross-repo/`, a separate
  source root) because it requires a same-org sibling checkout of
  `cloud-itonami-isic-0729` (`:local/root \"../cloud-itonami-isic-
  0729\"`, the SAME workspace-sibling convention this repo's own
  `io.github.kotoba-lang/robotics` dep already uses one org level up,
  and `steelworks`'s own `:cross-repo-test` alias already established
  one link earlier in the automotive chain this repo direct-ports)
  that a casual fork of just THIS repo would not have. Still no live
  network call between actors at runtime: this is a build-time
  classpath dependency exercised by tests, same category as every
  other `:local/root` dependency in this fleet."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.pedigree :as pedigree]
            [langgraph.graph :as g]
            [nonferrousops.export :as ore-export]
            [nonferrousops.store :as ore-store]
            [fab.export :as export]
            [fab.robotics :as robotics]
            [fab.governor :as governor]
            [fab.store :as store]
            [fab.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :fab-engineer :phase 3})

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

(defn- real-upstream-ore-pedigree
  "THE genuine cross-repo call: writes a real production record into
  a real `cloud-itonami-isic-0729` `nonferrousops.store` MemStore,
  reads it back out via that repo's OWN store protocol, and packages
  it via that repo's OWN `nonferrousops.export/pedigree-for-
  production-record` -- never a hand-typed EDN literal."
  [record-id site-id grade-actual quantity-tonnes issued-at]
  (let [st (ore-store/mem-store)
        st' (ore-store/add-production-record st record-id
                                              {:site-id site-id
                                               :commodity :copper
                                               :grade-actual grade-actual
                                               :grade-min 0.0 :grade-max 100.0
                                               :quantity-tonnes quantity-tonnes})
        rec (ore-store/production-record st' record-id)]
    (ore-export/pedigree-for-production-record rec issued-at)))

(deftest real-cross-repo-ore-pedigree-is-shape-valid
  (testing "a pedigree built from a REAL cloud-itonami-isic-0729 store round-trip passes kotoba.pedigree/valid?"
    (let [p (real-upstream-ore-pedigree "prod-strong" "copper-site-001" 26.5 3600.0 "2026-07-16")]
      (is (some? p))
      (is (true? (pedigree/valid? p)))
      (is (= "cloud-itonami-isic-0729" (:pedigree/issuing-actor p)))
      (testing "the claim is the REAL recorded reading, not invented -- independently re-reading the same production record yields the identical number"
        (is (= 26.5 (pedigree/claim-value p :grade-actual))
            "documents the actual real-record value, for a human reader's sanity")))))

(deftest genuine-2-hop-chain-is-shape-valid-end-to-end
  (testing "a lot pedigree built with a REAL embedded upstream ore pedigree (both hops genuine cross-repo/cross-store calls) is shape-valid end-to-end, and the chain is genuinely 2 levels deep from this repo's own vantage point (ore -> lot)"
    (let [ore (real-upstream-ore-pedigree "prod-strong" "copper-site-001" 26.5 3600.0 "2026-07-16")
          lot (merge {:id "lot-strong" :bond-wire-diameter-um 25.0 :upstream-ore-pedigree ore}
                     (robotics/bond-pull-telemetry-for {:bond-wire-diameter-um 25.0}))
          lot-pedigree (export/pedigree-for-lot lot "2026-07-16")]
      (is (true? (pedigree/valid? ore)))
      (is (true? (pedigree/valid? lot-pedigree)))
      (is (= ore (:pedigree/upstream lot-pedigree))
          "the REAL isic-0729 pedigree is embedded verbatim, not summarized/flattened")
      (testing "each hop's own claim stays independently readable"
        (is (= 26.5 (pedigree/claim-value (:pedigree/upstream lot-pedigree) :grade-actual)))
        (is (= (:bond-pull-strength-actual lot) (pedigree/claim-value lot-pedigree :bond-pull-strength-gf)))))))

(deftest real-cross-repo-ore-pedigree-genuinely-clears-fab-governor
  (testing "a rich-enough real ore production record's pedigree genuinely clears fab.governor's independent acceptance check end-to-end, and a real lot dispatches"
    (let [ore-pedigree (real-upstream-ore-pedigree "prod-strong" "copper-site-001" 26.5 3600.0 "2026-07-16")
          _ (is (>= (pedigree/claim-value ore-pedigree :grade-actual) governor/min-upstream-ore-grade-pct)
                "sanity: this record's REAL recorded grade actually clears fab's own disclosed floor")
          db (store/seed-db)
          actor (op/build db)]
      (verify! actor "e1pre" "lot-1")
      (simulate-robotics! actor "e1pre2" "lot-1")
      (attach-ore-pedigree! actor "e1pre3" "lot-1" ore-pedigree)
      (is (= ore-pedigree (:upstream-ore-pedigree (store/lot db "lot-1")))
          "the REAL cross-repo ore pedigree landed on the lot record unmodified")
      (let [res (exec-op actor "e1" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)]
        (is (= :interrupted (:status res))
            "governor's independent re-verification found no violation from the real ore pedigree -- escalates for human approval, same as any clean dispatch")
        (let [r2 (approve! actor "e1")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:process-step-dispatched? (store/lot db "lot-1")))))))))

(deftest real-cross-repo-ore-pedigree-genuinely-fails-fab-governor
  (testing "a too-lean real ore production record's pedigree genuinely fails fab.governor's independent acceptance check end-to-end -- HARD hold, derived from a REAL cloud-itonami-isic-0729 store round-trip, never a hand-crafted failing fixture"
    (let [ore-pedigree (real-upstream-ore-pedigree "prod-weak" "copper-site-002" 12.0 400.0 "2026-07-16")
          _ (is (< (pedigree/claim-value ore-pedigree :grade-actual) governor/min-upstream-ore-grade-pct)
                "sanity: this record's REAL recorded grade actually falls short of fab's own disclosed floor")
          db (store/seed-db)
          actor (op/build db)]
      (verify! actor "e2pre" "lot-1")
      (simulate-robotics! actor "e2pre2" "lot-1")
      (attach-ore-pedigree! actor "e2pre3" "lot-1" ore-pedigree)
      (let [res (exec-op actor "e2" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:upstream-ore-pedigree-claims-out-of-tolerance} (-> (store/ledger db) last :basis)))
        (is (empty? (store/dispatch-history db)))))))
