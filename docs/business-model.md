# Business Model: Semiconductor and Electronics Manufacturing Enablement

## Classification
- Repository: `cloud-itonami-isic-2610`
- ISIC Rev.5: `2610` — semiconductor and electronics manufacturing enablement — fab and test operations for small-batch/legacy nodes
- Social impact: supply-resilience industrial-jobs technology-access

## Customer
- small-batch fabs, electronics makers and reshoring programs that cannot accept closed MES lock-in

## Offer
- spec and process-step management, EDA/CAE verification, wafer-lot tracking, test and packaging records, yield audit

## Revenue
- setup fee per fab, monthly operations subscription, yield and integration services

## Trust Controls
- process steps outside spec are blocked; verification evidence is required; lot history is immutable
- a robot action the governor refuses is never dispatched to hardware
- every dispatch, hold, approval and disclosure path is auditable
- sensitive operating and personal data stays outside Git
- a fabricated process-safety citation, incomplete evidence, an
  unresolved process-defect flag, or an insufficient yield rate --
  each forces a hold, not an override
- yield-audit finalization is logged and escalated, and cannot be
  finalized twice for the same lot: a double-finalization attempt is
  held off this actor's own lot facts alone, with no upstream
  comparison needed
