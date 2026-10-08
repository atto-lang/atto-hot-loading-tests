# Test execution and honest coverage

## Requirements

- JDK 21+ (GraalVM JDK 25 preferred for runtime comparisons), Maven, and a clean checkout of the **private** `ores-truffle-oreslang/oreslang-source.java` repository.
- That checkout's HEAD must be **exactly** the SHA in [SOURCE_REF](../SOURCE_REF). The harness fails closed rather than silently testing a different source revision.
- The parent source repository has Maven artifact `dev.oreslang:oreslang-source:0.1.0-SNAPSHOT`; this harness first installs that artifact locally and then runs its own JUnit integration suite.

```bash
# Make both repos siblings; do not embed tokens in URLs or logs.
export ORESLANG_SOURCE_DIR="$(pwd)/../oreslang-source.java"
bash scripts/test.sh
```

The private source is **not** downloadable via a sibling repo's default `GITHUB_TOKEN`. Do not claim a green CI run without a real JVM/Graal test on the exact pinned tree. To use a funded test organization, mirror the exact source Git tree into an authenticated test repository through the GitHub connector, verify SHA/tree identity, then run this harness there; no cross-org secret checkout is needed. A mirror of this harness alone does **not** make the private source available.

## Coverage classification

| Area | Executable Java tests | Claims not yet proven |
| --- | --- | --- |
| Stage/start/retire/close | HotLoadingBaselineTest | Atomic promotion, durable routing |
| Parse, capabilities, quotas, digest | HotLoadingBaselineTest | Formal sandbox or physical per-actor isolation |
| Isolated/shared/untrusted actor lifecycle | HotLoadingActorTest | Cross-generation live actor mailboxes, production draining |
| Host-side epoch/canary fallback demonstration | HostReleaseCoordinatorTest | Runtime-owned supervisor or stable actor-router API |
| Desired atomic activation semantics | PendingRuntimeContractTest (disabled/red) | Must implement source runtime before enabling |
| OTP supervisor/link/monitor/release system | See OTP_UPGRADE_MATRIX.md | Not yet implemented or tested |

**Disabled tests are never counted as passes.** JIT execution in a JVM is not evidence of an AOT native executable test, and profile capability getters are not native-image runtime verification. The source is a private repository: this repo intentionally does not hardcode credentials, copy source code, or declare GitHub Actions green without source admission.

## Known baseline runtime gaps

Current `HotReloadManager.load()` both stages and changes `active()` and `active(codeUnitId)`. A start failure closes its `Generation` but leaves the manager's active pointer and live-generation registry referring to it. A trusted host may keep an independent routing pointer temporarily (demonstrated in the test-only coordinator), but the actual runtime should expose atomic `stage -> validate -> activate -> drain -> retire`, including compare-and-swap epoch fencing. See `PendingRuntimeContractTest`.

The JVM/JIT cache optimizes execution within a generation; it cannot change the language requirement that declarations and actor contracts are compiler-known and version-checked. Release migrations must be explicit.
