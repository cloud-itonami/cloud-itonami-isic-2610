(ns fab.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean lot through
  intake -> requirements verification -> process-defect screening ->
  process-step-dispatch proposal (always escalates) -> human approval
  -> commit, then through yield-audit proposal (always escalates) ->
  human approval -> commit, then shows five HARD holds (a jurisdiction
  with no spec-basis, an insufficient yield rate, an unresolved
  process-defect flag screened directly via `:defect/screen` [never
  via an actuation op against an unscreened lot -- see this actor's
  own governor ns docstring / the lesson `parksafety`'s
  ADR-2607071922 Decision 5, `eldercare`'s, `museum`'s,
  `conservation`'s, `salon`'s, `entertainment`'s, `casework`'s,
  `hospital`'s, `facility`'s, `school`'s, `association`'s, `leasing`'s,
  `behavioral`'s, `secondary`'s, `card`'s, `water`'s, `telecom`'s,
  `aerospace`'s, `recovery`'s, `consulting`'s, `union`'s and
  `congregation`'s ADR-0001s already recorded], and a double process-
  step-dispatch/yield-audit of an already-processed lot) that never
  reach a human at all, and prints the audit ledger + the draft
  process-step-dispatch and yield-audit records."
  (:require [langgraph.graph :as g]
            [fab.store :as store]
            [fab.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :fab-engineer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== lot/intake lot-1 (JPN, clean; 90/100 yield, no defect flag) ==")
    (println (exec! actor "t1" {:op :lot/intake :subject "lot-1"
                                :patch {:id "lot-1" :lot-name "Sakura Fab Lot 4"}} operator))

    (println "== requirements/verify lot-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :requirements/verify :subject "lot-1"} operator))
    (println (approve! actor "t2"))

    (println "== defect/screen lot-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :defect/screen :subject "lot-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/dispatch-process-step lot-1 (always escalates -- actuation/dispatch-process-step) ==")
    (let [r (exec! actor "t4" {:op :actuation/dispatch-process-step :subject "lot-1"} operator)]
      (println r)
      (println "-- human fab engineer approves --")
      (println (approve! actor "t4")))

    (println "== actuation/finalize-yield-audit lot-1 (always escalates -- actuation/finalize-yield-audit) ==")
    (let [r (exec! actor "t5" {:op :actuation/finalize-yield-audit :subject "lot-1"} operator)]
      (println r)
      (println "-- human fab engineer approves --")
      (println (approve! actor "t5")))

    (println "== requirements/verify lot-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :requirements/verify :subject "lot-2" :no-spec? true} operator))

    (println "== requirements/verify lot-3 (escalates -- human approves; sets up the insufficient-yield test) ==")
    (println (exec! actor "t7" {:op :requirements/verify :subject "lot-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/finalize-yield-audit lot-3 (70/100 = 0.70 below 0.85 required -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/finalize-yield-audit :subject "lot-3"} operator))

    (println "== defect/screen lot-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :defect/screen :subject "lot-4"} operator))

    (println "== actuation/dispatch-process-step lot-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/dispatch-process-step :subject "lot-1"} operator))

    (println "== actuation/finalize-yield-audit lot-1 AGAIN (double-finalization -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/finalize-yield-audit :subject "lot-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft process-step-dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft yield-audit records ==")
    (doseq [r (store/audit-history db)] (println r))))
