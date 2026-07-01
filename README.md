# cloud-itonami-2610

Open Business Blueprint for **ISIC Rev.5 2610**: semiconductor and electronics manufacturing enablement — fab and test operations for small-batch/legacy nodes.

This repository designs a forkable OSS business for semiconductor and electronics manufacturing enablement:
run by a qualified operator so a community keeps its own operating records
instead of renting a closed SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a fab robot handles wafer transport, process step execution and inspection in the cleanroom under an actor that proposes
actions and an independent **Fab Operations Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
handling wafers, chemicals and expensive/in-critical-supply lots) require human sign-off.

## Core Contract

```text
intake + identity + eda records
        |
        v
Advisor -> Fab Operations Governor -> proceed, hold, or human approval
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `2610`). Required capabilities:

- `:robotics`
- `:eda`
- `:cae`
- `:dmn`
- `:bpmn`
- `:audit-ledger`

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
