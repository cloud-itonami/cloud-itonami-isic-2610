(ns fab.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300):
  this repo previously shipped a ONE-TIME hand-pasted robotics stub at
  `docs/samples/operator-console.html` (generic missions/actions, no lot
  ids, no real actor run). This namespace drives the REAL actor stack
  (`fab.operation` -> `fab.governor` -> `fab.store`) through a scenario
  adapted from this repo's own `fab.sim` demo driver (`clojure -M:dev:run`,
  confirmed to use seeded lot ids that match `fab.store/demo-data` and to
  exercise real physics-2d wire-bond pull-test simulation via
  `fab.robotics`/`fab.simphysics`), and renders the result
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed (verify by
  diffing two consecutive runs before shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [fab.store :as store]
            [fab.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :fab-engineer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through the same interesting-state scenario
  `fab.sim` already exercises: lot-1 clears intake (phase-3 auto) ->
  requirements verify (approved) -> defect screen (approved) ->
  robotics wire-bond pull-test simulation (approved, real physics-2d) ->
  process-step dispatch (always escalate, approved) -> yield-audit
  finalize (always escalate, approved); lot-2 HARD-holds on no
  process-safety spec-basis; lot-3 HARD-holds on insufficient yield
  rate; lot-5 HARD-holds on independent robotics recheck out of
  tolerance (deliberately-too-thin bond wire); lot-4 HARD-holds on an
  unresolved process-defect flag; lot-1 double-dispatch and
  double-yield-audit HARD-hold. Every HARD hold never reaches a human.
  Returns the resulting store -- every field `render` reads is real
  governor/store output."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "t1" {:op :lot/intake :subject "lot-1"
                       :patch {:id "lot-1" :lot-name "Sakura Fab Lot 4"}})
    (exec! actor "t2" {:op :requirements/verify :subject "lot-1"})
    (approve! actor "t2")
    (exec! actor "t3" {:op :defect/screen :subject "lot-1"})
    (approve! actor "t3")
    ;; missing robotics sim -> HARD hold (distinct reason)
    (exec! actor "t3a" {:op :actuation/dispatch-process-step :subject "lot-1"})
    (exec! actor "t3b" {:op :robotics/simulate-process-step :subject "lot-1"})
    (approve! actor "t3b")
    (exec! actor "t4" {:op :actuation/dispatch-process-step :subject "lot-1"})
    (approve! actor "t4")
    (exec! actor "t5" {:op :actuation/finalize-yield-audit :subject "lot-1"})
    (approve! actor "t5")

    (exec! actor "t6" {:op :requirements/verify :subject "lot-2" :no-spec? true})

    (exec! actor "t7" {:op :requirements/verify :subject "lot-3"})
    (approve! actor "t7")
    (exec! actor "t8" {:op :actuation/finalize-yield-audit :subject "lot-3"})

    (exec! actor "t8b" {:op :requirements/verify :subject "lot-5"})
    (approve! actor "t8b")
    (exec! actor "t8c" {:op :actuation/dispatch-process-step :subject "lot-5"})

    (exec! actor "t9" {:op :defect/screen :subject "lot-4"})

    (exec! actor "t10" {:op :actuation/dispatch-process-step :subject "lot-1"})
    (exec! actor "t11" {:op :actuation/finalize-yield-audit :subject "lot-1"})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- yield-cell [{:keys [good-dies total-dies required-yield-share]}]
  (if (and (number? good-dies) (number? total-dies) (pos? total-dies)
           (number? required-yield-share))
    (let [rate (/ (double good-dies) total-dies)]
      (if (< rate required-yield-share)
        (format "<span class=\"err\">%.2f &lt; %.2f required</span>"
                rate (double required-yield-share))
        (format "<span class=\"ok\">%.2f &ge; %.2f</span>"
                rate (double required-yield-share))))
    "<span class=\"muted\">n/a</span>"))

(defn- defect-cell [{:keys [process-defect-flag-unresolved?]}]
  (if process-defect-flag-unresolved?
    "<span class=\"err\">unresolved</span>"
    "<span class=\"ok\">clear</span>"))

(defn- bond-cell [{:keys [bond-wire-diameter-um bond-pull-strength-actual
                          bond-pull-strength-min bond-pull-strength-max]}]
  (if (and (number? bond-pull-strength-actual)
           (number? bond-pull-strength-min)
           (number? bond-pull-strength-max))
    (if (<= bond-pull-strength-min bond-pull-strength-actual bond-pull-strength-max)
      (format "<span class=\"ok\">%.1f gf &isin; [%.1f,%.1f] · %.1f &mu;m wire</span>"
              (double bond-pull-strength-actual)
              (double bond-pull-strength-min)
              (double bond-pull-strength-max)
              (double (or bond-wire-diameter-um 0.0)))
      (format "<span class=\"err\">%.1f gf out of [%.1f,%.1f] · %.1f &mu;m wire</span>"
              (double bond-pull-strength-actual)
              (double bond-pull-strength-min)
              (double bond-pull-strength-max)
              (double (or bond-wire-diameter-um 0.0))))
    "<span class=\"muted\">no telemetry</span>"))

(defn- status-cell [ledger {:keys [id process-step-dispatched? yield-audit-finalized?]}]
  (let [f (last-fact-for ledger id)]
    (cond
      (and process-step-dispatched? yield-audit-finalized?)
      "<span class=\"ok\">dispatched &amp; yield-audited</span>"

      (or (= :governor-hold (:t f)) (= :hold (:disposition f)))
      (let [rule (or (-> f :violations first :rule)
                     (first (:basis f)))]
        (case rule
          :no-spec-basis
          "<span class=\"critical\">HARD hold &middot; no spec-basis</span>"
          :process-defect-flag-unresolved
          "<span class=\"critical\">HARD hold &middot; process defect</span>"
          :yield-rate-insufficient
          "<span class=\"critical\">HARD hold &middot; yield insufficient</span>"
          :robotics-simulation-missing
          "<span class=\"critical\">HARD hold &middot; robotics sim missing</span>"
          :robotics-simulation-out-of-tolerance
          "<span class=\"critical\">HARD hold &middot; bond-pull out of tolerance</span>"
          :already-dispatched
          "<span class=\"critical\">HARD hold &middot; double dispatch</span>"
          :already-audited
          "<span class=\"critical\">HARD hold &middot; double yield-audit</span>"
          (str "<span class=\"critical\">HARD hold &middot; "
               (esc (name (or rule :unknown))) "</span>")))

      (= :approval-granted (:t f))
      "<span class=\"ok\">approved &amp; committed</span>"

      (= :committed (:t f))
      "<span class=\"ok\">committed</span>"

      (= :approval-requested (:t f))
      "<span class=\"warn\">awaiting approval</span>"

      :else "<span class=\"muted\">in progress</span>")))

(defn- lot-row [ledger {:keys [id lot-name jurisdiction] :as lot}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc lot-name) (esc jurisdiction)
          (yield-cell lot) (defect-cell lot) (bond-cell lot)
          (status-cell ledger lot)))

(defn- kw-label [k]
  (if (keyword? k)
    (if-let [ns (namespace k)]
      (str ns "/" (name k))
      (name k))
    (str k)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (kw-label t)) (esc (if op (kw-label op) "n-a")) (esc subject)
          (esc (or (some->> basis (map kw-label) (str/join ", "))
                   (some-> disposition kw-label)
                   ""))))

(defn- record-row [record kind note]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (get record "record_id")) (esc kind) (esc note)))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract (README Ops /
  ;; fab.governor / fab.phase) -- documentation of fixed behavior, not
  ;; runtime telemetry, so it is legitimately hand-described.
  ["        <tr><td><code>:lot/intake</code></td><td>none</td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:requirements/verify</code></td><td>none</td><td><span class=\"warn\">human approval &middot; official process-safety citation only</span></td></tr>"
   "        <tr><td><code>:defect/screen</code></td><td>none</td><td><span class=\"critical\">HARD hold if unresolved process-defect flag</span></td></tr>"
   "        <tr><td><code>:robotics/simulate-process-step</code></td><td>none</td><td><span class=\"warn\">human approval &middot; real physics-2d wire-bond pull-test</span></td></tr>"
   "        <tr><td><code>:actuation/dispatch-process-step</code></td><td class=\"critical\">safety-critical</td><td><span class=\"warn\">always human &middot; robotics recheck + defect + double-dispatch guards</span></td></tr>"
   "        <tr><td><code>:actuation/finalize-yield-audit</code></td><td class=\"critical\">business-critical</td><td><span class=\"warn\">always human &middot; independent yield-rate recompute + double-audit guard</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        lots (->> (store/all-lots db)
                  (filter #(#{"lot-1" "lot-2" "lot-3" "lot-4" "lot-5"} (:id %)))
                  (sort-by :id))
        dispatches (store/dispatch-history db)
        audits (store/audit-history db)
        lot-rows (str/join "\n" (map (partial lot-row ledger) lots))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        dispatch-rows (str/join "\n" (map #(record-row % "process-step-dispatch-draft"
                                                       "unsigned · fab operator signs offline")
                                          dispatches))
        audit-rows (str/join "\n" (map #(record-row % "yield-audit-draft"
                                                    "unsigned · shipment/certification is plant act")
                                       audits))
        handoff-rows (str/join "\n" (remove str/blank? [dispatch-rows audit-rows]))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-2610 &middot; semiconductor fab</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Semiconductor / electronics fab (ISIC 2610) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · never dispatches cleanroom hardware</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Lots</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>fab.store</code> via <code>fab.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated when the actor's real behavior changes.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Lot</th><th>Name</th><th>Jurisdiction</th><th>Yield</th><th>Defect flag</th><th>Bond-pull (physics-2d)</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     lot-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Fab Operations Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Process-step dispatch and yield-audit finalization always escalate to a human fab engineer; bond-pull strength is independently re-simulated, never trusted from a stored self-report.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Stake</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Social hand-off</h2>\n"
     "    <p class=\"muted\">Unsigned draft process-step-dispatch and yield-audit records for plant MES / process-safety inspectors — registry drafts only, never live cleanroom control.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Artifact</th><th>Kind</th><th>Note</th></tr></thead>\n"
     "      <tbody>\n"
     handoff-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/dispatch-history db)) "process-step dispatches,"
             (count (store/audit-history db)) "yield audits )")))
