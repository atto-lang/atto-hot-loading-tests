# Oreslang hot-loading & OTP-style supervision conformance

**Status: audited draft, source-backed JUnit integration not yet verified because GitHub Actions has not allocated a runner.** This repo intentionally distinguishes existing generation APIs from the OTP-like supervisor/release capabilities Oreslang still needs.

## What is included

- **16 baseline tests** for `HotReloadManager` staging, no-run-before-start, once-only entrypoint, SHA-256, independent guest contexts, multiple code units, retention/retirement, cleanup, capability admission, and hostile source limits.
- **7 original executable actor scenarios, plus 12 upgrade edge-case tests and 8 incremental-compiler/ABI tests** exercising actual Oreslang source fixtures: isolated actors (v1 and v2), shared actors, untrusted actors, startup failure containment, and multiple independently retired units.
- **12 more real-runtime edge cases** in `HotLoadingUpgradeEdgeCasesTest` (two-turn actor mailboxes in distinct code versions; malformed replacement admission; version IDs; retire isolation; per-unit routing; quota recovery). All use the pinned Graal/Oreslang implementation, not a fake interpreter.
- **8 static-ABI and incremental-compiler tests** in `HotLoadingStaticAbiTest`: the code images have compiler-known source/ABI digests, body-only updates preserve ABI, public API changes are detectable, and reverting to a previous image still creates a new isolated generation. Static ABI admission during activation remains pending.
- **8 trusted-host release-controller tests** for canary/epoch/fallback/drain *as a test-only proof*, not an assertion that the actual runtime has OTP supervisors.
- **6 disabled failing-contract regression gates**, which must be enabled when atomic runtime activation and rollback are implemented. Disabled cases are *not* green.
- **45 OTP parity scenarios** specifying required future behavior for supervision, release upgrades, message ABI, migration, links, monitors, distributed actor fencing, multi-generation memory safety, and JIT/AOT parity.

The 12 new tests are **source-backed, not yet executed in CI**; two-message guest fixtures deliberately assert the actual values from each mailbox turn. Passing `start()` alone is not a substitute for actual async completion; the existing async negative control remains required. These do not prove runtime-owned OTP supervision or migration.

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

## GitHub Actions admission

The `.github/workflows/conformance.yml` workflow runs a Rust-only fixture inventory followed by **required source-backed Maven/GraalVM integration**. The latter checks out branch `validation/source-9d189677` **from this same repository**, or an explicitly supplied source-mirror ref on manual dispatch. The source mirror must already contain the entire pinned source Git tree (not just the harness), with exact `SOURCE_TREE_REF` identity. If no mirror exists, integration **fails closed**; a green fixture-audit result alone is never enough to merge. Zero-step GitHub Actions failures are runner/admission failures, not source-code test outcomes.
