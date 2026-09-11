(ns fab.phase
  "Phase 0->3 staged rollout -- the fab-operator analog of `cloud-
  itonami-isic-6512`'s `casualty.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- lot intake allowed, every write needs
                                 human approval.
    Phase 2  assisted-verify  -- adds requirements verification +
                                 process-defect-flag screening + robot
                                 wafer-probe/wire-bond verification
                                 writes, still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:lot/intake` (no capital risk yet)
                                 may auto-commit. `:actuation/
                                 dispatch-process-step`/`:actuation/
                                 finalize-yield-audit` NEVER auto-
                                 commit, at any phase.

  `:actuation/dispatch-process-step`/`:actuation/finalize-yield-audit`
  are deliberately ABSENT from every phase's `:auto` set, including
  phase 3 -- a permanent structural fact, not a rollout milestone
  still to come. Dispatching a real robot process-step action in the
  cleanroom and finalizing a real yield-audit record are the two
  real-world acts this actor performs; both are always a human fab
  engineer's call. `fab.governor`'s `:actuation/dispatch-process-
  step`/`:actuation/finalize-yield-audit` high-stakes gate enforces
  the same invariant independently -- two layers, not one, agree on
  this. `:defect/screen`/`:robotics/simulate-process-step` are
  likewise never auto-eligible, at any phase -- the same posture every
  sibling's screening/verification op has. Phase 3's `:auto` set here
  has only ONE member (`:lot/intake`) -- this domain has no separate
  no-capital-risk 'file' lifecycle distinct from the lot record
  itself.")

(def read-ops  #{})
(def write-ops #{:lot/intake :requirements/verify :defect/screen
                 :robotics/simulate-process-step
                 :actuation/dispatch-process-step :actuation/finalize-yield-audit})

;; NOTE the invariant: `:actuation/dispatch-process-step`/`:actuation/
;; finalize-yield-audit` are members of `write-ops` (governor-gated
;; like any write) but are NEVER members of any phase's `:auto` set
;; below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                          :auto #{}}
   1 {:label "assisted-intake"  :writes #{:lot/intake}                                               :auto #{}}
   2 {:label "assisted-verify"  :writes #{:lot/intake :requirements/verify :defect/screen
                                          :robotics/simulate-process-step}          :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:lot/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:actuation/dispatch-process-step`/`:actuation/finalize-yield-
    audit` are never auto-eligible at any phase, so they always
    escalate once the governor clears them (or hold if the governor
    doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Fab Operations Governor verdict to a base disposition before
  the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
