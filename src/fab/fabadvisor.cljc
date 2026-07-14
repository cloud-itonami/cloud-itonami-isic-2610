(ns fab.fabadvisor
  "Fab Advisor client -- the *contained intelligence node* for the
  semiconductor-fab actor.

  It normalizes lot-intake, drafts a per-jurisdiction process-safety
  evidence checklist, screens lots for an unresolved process-defect
  flag, drafts the process-step-dispatch action, and drafts the
  yield-audit-finalization action. CRITICAL: it is a smart-but-
  untrusted advisor. It returns a *proposal* (with a rationale + the
  fields it cited), never a committed record or a real robot dispatch/
  yield-audit finalization. Every output is censored downstream by
  `fab.governor` before anything touches the SSoT, and `:actuation/
  dispatch-process-step`/`:actuation/finalize-yield-audit` proposals
  NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/dispatch-process-step | :actuation/finalize-yield-audit | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [fab.facts :as facts]
            [fab.registry :as registry]
            [fab.robotics :as robotics]
            [fab.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the lot, yield figures or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "ロット記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :lot/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-requirements
  "Per-jurisdiction process-safety evidence checklist draft. `:no-
  spec?` injects the failure mode we must defend against: proposing a
  checklist for a jurisdiction with NO official spec-basis in `fab.
  facts` -- the Fab Operations Governor must reject this (never invent
  a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [l (store/lot db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction l))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "fab.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :verification/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :verification/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-defect
  "Process-defect screening draft. `:process-defect-flag-unresolved?`
  on the lot record injects the failure mode: the Fab Operations
  Governor must HOLD, un-overridably, on any unresolved flag."
  [db {:keys [subject]}]
  (let [l (store/lot db subject)]
    (cond
      (nil? l)
      {:summary "対象ロット記録が見つかりません" :rationale "no lot record"
       :cites [] :effect :defect-screen/set :value {:lot-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:process-defect-flag-unresolved? l))
      {:summary    (str (:lot-name l) ": 未解決の工程欠陥フラグを検出")
       :rationale  "スクリーニングが未解決の工程欠陥フラグを検出。人手確認とホールドが必須。"
       :cites      [:defect-check]
       :effect     :defect-screen/set
       :value      {:lot-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:lot-name l) ": 未解決の工程欠陥フラグなし")
       :rationale  "工程欠陥スクリーニング完了。"
       :cites      [:defect-check]
       :effect     :defect-screen/set
       :value      {:lot-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- simulate-process-step
  "Runs the robot wafer-probe/optical-inspection/wire-bond-pull-test
  verification mission (`fab.robotics`) and drafts its result as a
  proposal. High confidence -- the mission itself is deterministic
  simulated telemetry derived from the lot's own recorded bond-pull-
  strength fields, not an LLM guess; the Fab Operations Governor still
  independently re-derives :passed? from those same fields before any
  `:actuation/dispatch-process-step` proposal may commit -- see `fab.
  governor`'s `robotics-simulation-violations`."
  [db {:keys [subject]}]
  (let [l (store/lot db subject)]
    (if (nil? l)
      {:summary "対象ロット記録が見つかりません" :rationale "no lot record"
       :cites [] :effect :lot/upsert :value {:id subject :robotics-sim-verified? false}
       :stake nil :confidence 0.0}
      (let [{:keys [mission actions passed?]} (robotics/simulate-process-step subject l)]
        {:summary    (str subject ": ウェハープローブ/ワイヤーボンド検証ミッション " (if passed? "合格" "不合格"))
         :rationale  (str "mission=" (:mission/id mission) " actions=" (count actions)
                          " bond-pull-strength-actual=" (:bond-pull-strength-actual l))
         :cites      [(:mission/id mission)]
         :effect     :lot/upsert
         :value      {:id subject
                      :robotics-sim-verified? passed?
                      :robotics-sim-record {:mission-id (:mission/id mission)
                                            :actions (mapv #(dissoc % :action) actions)
                                            :passed? passed?}}
         :stake      nil
         :confidence 0.95}))))

(defn- propose-process-step-dispatch
  "Draft the actual PROCESS-STEP-DISPATCH action -- dispatching a
  real robot process-step action in the cleanroom. ALWAYS `:stake
  :actuation/dispatch-process-step` -- this is a REAL-WORLD safety-
  critical act, never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`fab.phase`); the governor also always escalates on `:actuation/
  dispatch-process-step`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [l (store/lot db subject)]
    {:summary    (str subject " 向け工程実行提案"
                      (when l (str " (lot=" (:lot-name l) ")")))
     :rationale  (if l
                   "process-defect-screen referenced"
                   "ロット記録が見つかりません")
     :cites      (if l [subject] [])
     :effect     :lot/mark-dispatched
     :value      {:lot-id subject}
     :stake      :actuation/dispatch-process-step
     :confidence (if l 0.9 0.3)}))

(defn- propose-yield-audit
  "Draft the actual YIELD-AUDIT action -- finalizing a real yield-
  audit record for a lot ahead of shipment/certification. ALWAYS
  `:stake :actuation/finalize-yield-audit` -- this is a REAL-WORLD
  business-critical act, never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`fab.phase`); the governor also always escalates on
  `:actuation/finalize-yield-audit`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [l (store/lot db subject)]
    {:summary    (str subject " 向け歩留まり監査確定提案"
                      (when l (str " (lot=" (:lot-name l) ")")))
     :rationale  (if l
                   (str "good-dies=" (:good-dies l) " total-dies=" (:total-dies l)
                        " required-yield-share=" (:required-yield-share l))
                   "ロット記録が見つかりません")
     :cites      (if l [subject] [])
     :effect     :lot/mark-audited
     :value      {:lot-id subject}
     :stake      :actuation/finalize-yield-audit
     :confidence (if (and l (not (registry/yield-rate-insufficient? l))) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :lot/intake                            (normalize-intake db request)
    :requirements/verify                   (verify-requirements db request)
    :defect/screen                         (screen-defect db request)
    :robotics/simulate-process-step        (simulate-process-step db request)
    :actuation/dispatch-process-step        (propose-process-step-dispatch db request)
    :actuation/finalize-yield-audit         (propose-yield-audit db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは半導体ファブ事業者の工程実行・歩留まり監査確定エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:lot/upsert|:verification/set|:defect-screen/set|"
       ":lot/mark-dispatched|:lot/mark-audited) "
       "(:robotics/simulate-process-step も :lot/upsert で "
       ":robotics-sim-verified? を提案する) "
       ":stake(:actuation/dispatch-process-step か :actuation/finalize-yield-audit か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :requirements/verify                    {:lot (store/lot st subject)}
    :defect/screen                          {:lot (store/lot st subject)}
    :robotics/simulate-process-step         {:lot (store/lot st subject)}
    :actuation/dispatch-process-step        {:lot (store/lot st subject)}
    :actuation/finalize-yield-audit         {:lot (store/lot st subject)}
    {:lot (store/lot st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Fab Operations Governor
  escalates/holds -- an LLM hiccup can never auto-dispatch a process
  step or auto-finalize a yield audit."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :fabadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
