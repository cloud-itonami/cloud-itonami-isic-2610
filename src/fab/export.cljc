(ns fab.export
  "Cross-actor supply-chain-linkage export (ADR-2607999980, direct
  port of ADR-2607999950/extended by ADR-2607999960/ADR-2607999970's
  pattern to the smartphone chain: `cloud-itonami-isic-0729`
  non-ferrous metal ore mining -> `cloud-itonami-isic-2610`
  semiconductor/electronics fab (THIS actor) -> `cloud-itonami-
  isic-2630` communication-equipment assembly). `pedigree-for-lot`
  is this actor's own `steelworks.export/pedigree-for-heat`/
  `autoparts.export/pedigree-for-part-lot` equivalent -- same shape,
  same `kotoba.pedigree` contract, same 'pure data transform over
  data already on file, never a live network call and never an
  invented claim' discipline every prior export fn in this fleet
  already establishes.

  UNLIKE `nonferrousops.export` (the upstream, coordination-only
  origin of this chain), this fn's evidentiary basis IS a REAL
  `physics-2d` time-stepped rigid-body simulation reading -- fully
  reproducible, deterministic, re-runnable from the same inputs to
  the identical number: `fab.robotics/bond-pull-telemetry-for`
  actually runs `fab.simphysics`'s wire-bond destructive pull-test
  simulation (ADR-2607152000), and `:bond-pull-strength-actual`
  (mapped onto the `:bond-pull-strength-gf` claim below) is the real
  simulated force reading it produces, never a hand-set value.

  Unlike `steelworks.export`/`autoparts.export`, this actor is scoped
  ONLY to the cross-actor pedigree-export contract -- this repo does
  NOT (yet) have a general social/regulatory audit-package export
  namespace (`blueprint.edn` does not declare an `:export?`
  capability for this actor), so `fab.export` intentionally contains
  ONLY `pedigree-for-lot`, not an `audit-package`/`package->csv-
  bundle` pair. Adding those would be a separate, out-of-scope
  change; this namespace does not claim to provide them."
  (:require [kotoba.pedigree :as pedigree]))

(defn pedigree-for-lot
  "Builds a `kotoba.pedigree` record (a material-pedigree/wafer-lot-
  traceability-record-equivalent EDN interchange record -- direct
  port of `steelworks.export/pedigree-for-heat`) for `lot`, a lot
  record that ALREADY carries its own real, already-simulated
  wire-bond pull-test telemetry on file (`:bond-pull-strength-
  actual`, from `fab.robotics/bond-pull-telemetry-for` --
  ADR-2607152000's real `physics-2d` time-stepped wire-bond
  destructive pull-test simulation). This fn does NOT run that
  simulation itself -- it only packages a reading already on the lot
  map, mirroring how every other `pedigree-for-*` fn in this fleet
  only ever materializes a package body over data already on file,
  never computes new evidence.

  `issued-at` (an ISO date string) is a caller-supplied argument, not
  a wall-clock read -- this fn stays pure/deterministic, the same
  discipline every sibling `pedigree-for-*` fn already establishes.

  `:pedigree/claims` reports `:bond-pull-strength-gf` -- a FORCE
  reading in grams-force, honestly named after `fab.robotics`'s own
  field (`:bond-pull-strength-actual`, itself `= :sim-bond-pull-
  force-gf`, see that ns's docstring). `:pedigree/evidence-basis`
  cites the real simulation function that derived the reading, never
  a self-reported checklist string.

  Returns nil (never a fabricated pedigree) when `lot` carries no
  real `:bond-pull-strength-actual` on file -- the SAME disclosed
  'missing telemetry != inventable' discipline `fab.robotics` ns
  docstring / `simulation-out-of-tolerance?` already establish.

  Genuine 2-hop chaining (ADR-2607999980, mirroring ADR-2607999970's
  3-hop steel/ore chaining): when `lot` itself already carries an
  `:upstream-ore-pedigree` (a `kotoba.pedigree` record an upstream
  `cloud-itonami-isic-0729` non-ferrous-ore production record issued
  via `nonferrousops.export/pedigree-for-production-record`, and this
  actor's OWN governor independently re-verified before ever letting
  the lot's process step dispatch -- see `fab.governor`'s
  `upstream-ore-pedigree-claims-out-of-tolerance-violations`), it is
  embedded here as `:pedigree/upstream` (`kotoba.pedigree/claim`'s
  `:upstream` option, ADR-2607999960), producing a genuine 2-hop
  provenance chain (non-ferrous ore -> fab lot -> ...) `cloud-
  itonami-isic-2630`'s own governor can independently re-verify
  shape-wise via `kotoba.pedigree/valid?`'s recursive check -- never
  a bare id the receiver has to go look up, and never a second
  network fetch. When `lot` carries no `:upstream-ore-pedigree`,
  `:pedigree/upstream` is simply omitted -- a single-hop lot pedigree
  is unaffected by this option's existence, the exact same additive
  shape every sibling `pedigree-for-*` fn already established for ITS
  OWN `:upstream-*-pedigree` option."
  [{:keys [id bond-pull-strength-actual upstream-ore-pedigree]} issued-at]
  (when (and id (number? bond-pull-strength-actual))
    (pedigree/claim
     (str "PEDIGREE-" id) id "cloud-itonami-isic-2610"
     {:bond-pull-strength-gf bond-pull-strength-actual}
     :evidence-basis ["fab.robotics/bond-pull-telemetry-for (physics-2d time-stepped rigid-body simulation, wire-bond destructive pull-test reinterpretation -- see ns docstring)"]
     :issued-at issued-at
     :upstream upstream-ore-pedigree)))
