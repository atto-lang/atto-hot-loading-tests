# Oreslang hot-loading & OTP-style supervision conformance

**Status: initial draft, real-JVM integration suite written but not yet executed against the pinned private source runtime.** This repo intentionally distinguishes existing generation APIs from the OTP-like supervisor/release capabilities Oreslang still needs.

## What is included

- **16 baseline tests** for `HotReloadManager` staging, no-run-before-start, once-only entrypoint, SHA-256, independent guest contexts, multiple code units, retention/retirement, cleanup, capability admission, and hostile source limits.
- **7 executable actor scenarios** exercising actual Oreslang source fixtures: isolated actors (v1 and v2), shared actors, untrusted actors, startup failure containment, and multiple independently retired units.
- **8 trusted-host release-controller tests** for canary/epoch/fallback/drain *as a test-only proof*, not an assertion that the actual runtime has OTP supervisors.
- **6 disabled failing-contract regression gates**, which must be enabled when atomic runtime activation and rollback are implemented. Disabled cases are *not* green.
- **30 OTP parity scenarios** specifying required future behavior for supervision, release upgrades, message ABI, migration, links, monitors, distributed actor fencing, multi-generation memory safety, and JIT/AOT parity.

## Getting started

Clone this repo alongside an authenticated checkout of the private source runtime at the **exact** source commit and **tree identity** recorded in [SOURCE_REF](SOURCE_REF) and [SOURCE_TREE_REF](SOURCE_TREE_REF), then run:

```sh
export ORESLANG_SOURCE_DIR="../oreslang-source.java"
bash scripts/test.sh
```

Requires Maven and JDK 21+ (GraalVM JDK 25 preferred). The script verifies the exact tree identity (including a same-tree cross-repo mirror) and clean source checkout, installs the source artifact locally, and runs these JUnit tests. It does *not* fetch source across private org boundaries, use hidden tokens, or silently accept a mismatched source.

## Roadmap and guardrails

- [Runtime execution and evidence status](docs/RUNNING.md)
- [OTP release/upgrade conformance matrix](docs/OTP_UPGRADE_MATRIX.md)
- [Source runtime](https://github.com/ores-truffle-oreslang/oreslang-source.java)

Today, `HotReloadManager.load()` updates its `active` pointer *before* the staged generation starts. A failing start closes the generation but does **not** roll back that pointer or remove it from its registry. This is unsafe to use directly as a production route switch; the draft tests document the behavior and include explicit disabled regression tests for the desired atomic protocol.

**Execution models:** JIT may optimize immutable code images, but it must not create new guest-visible class/module/actor types at runtime. Native AOT still loads vetted guest source/IR through the already-compiled interpreter; guest JIT acceleration is an optimization, not a semantic dependency. Production isolated-actor hot upgrades are the first priority, then supervised subtrees/shared actors, and finally distributed OTP-like releases.
