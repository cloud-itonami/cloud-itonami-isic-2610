(ns fab.facts
  "Per-jurisdiction semiconductor-fab process-safety regulatory
  catalog -- the G2-style spec-basis table the Fab Operations
  Governor checks every requirements/verify proposal against ('did
  the advisor cite an OFFICIAL public source for this jurisdiction's
  fab process-safety requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values cite each jurisdiction's official chemical/process-
  safety regulator (see `:provenance`), alongside SEMI (Semiconductor
  Equipment and Materials International) international process
  standards referenced across all four -- SEMI standards are an
  industry body, not a government, so they supplement rather than
  replace each jurisdiction's own binding safety law. This is a
  STARTING catalog, not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  process-spec-verification-record/EDA-CAE-simulation-record/
  chemical-safety-clearance-record/wafer-lot-traceability-record
  evidence set submitted in some form; `:legal-basis` / `:owner-
  authority` / `:provenance` are the G2 citation the governor requires
  before any `:requirements/verify` proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "経済産業省 (METI, Ministry of Economy, Trade and Industry) / SEMI (Semiconductor Equipment and Materials International)"
          :legal-basis "高圧ガス保安法 (High Pressure Gas Safety Act) / SEMI International Standards"
          :national-spec "半導体製造プロセスの化学物質・高圧ガス取扱い安全基準"
          :provenance "https://www.meti.go.jp/policy/safety_security/industrial_safety/sangyo/hipregas/"
          :required-evidence ["工程仕様検証記録 (process-spec-verification-record)"
                              "EDA/CAEシミュレーション記録 (EDA-CAE-simulation-record)"
                              "化学物質安全許可記録 (chemical-safety-clearance-record)"
                              "ウェハーロット追跡記録 (wafer-lot-traceability-record)"]}
   "USA" {:name "United States"
          :owner-authority "Occupational Safety and Health Administration (OSHA) / SEMI International Standards"
          :legal-basis "OSHA Process Safety Management standard (29 CFR 1910.119) / SEMI International Standards"
          :national-spec "Semiconductor-fab chemical/gas-handling process-safety requirements"
          :provenance "https://www.osha.gov/process-safety-management"
          :required-evidence ["Process-spec-verification record"
                              "EDA/CAE-simulation record"
                              "Chemical-safety-clearance record"
                              "Wafer-lot-traceability record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Health and Safety Executive (HSE) / SEMI International Standards"
          :legal-basis "Control of Major Accident Hazards Regulations 2015 (COMAH) / SEMI International Standards"
          :national-spec "Semiconductor-fab major-hazard chemical/gas-handling requirements"
          :provenance "https://www.hse.gov.uk/comah/"
          :required-evidence ["Process-spec-verification record"
                              "EDA/CAE-simulation record"
                              "Chemical-safety-clearance record"
                              "Wafer-lot-traceability record"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesanstalt für Arbeitsschutz und Arbeitsmedizin (BAuA) / SEMI International Standards"
          :legal-basis "Gefahrstoffverordnung (GefStoffV, Hazardous Substances Ordinance) / SEMI International Standards"
          :national-spec "Halbleiterfertigungs-Chemikalien- und Gasumgangssicherheitsanforderungen"
          :provenance "https://www.baua.de/DE/Themen/Anwendungssichere-Chemikalien-und-Biostoffe/Gefahrstoffe/Gefahrstoffe_node.html"
          :required-evidence ["Prozessspezifikationsprüfnachweis (process-spec-verification-record)"
                              "EDA/CAE-Simulationsnachweis (EDA-CAE-simulation-record)"
                              "Chemikaliensicherheitsfreigabe (chemical-safety-clearance-record)"
                              "Wafer-Los-Rückverfolgbarkeitsnachweis (wafer-lot-traceability-record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch a
  process step or finalize a yield audit on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-2610 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `fab.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
