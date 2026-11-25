# UHL-1 five-backend full-AOT hot-loading acceptance gate (proposal)

This is a **fail-closed acceptance manifest** for the existing offline
`ores-rollout-evidence` checker. It extends the prior four Java baseline
rows with four explicit cases per backend (Java/GraalVM, LLVM, Wasm,
Forge, .NET). All 20 added entries are **targets, NOT_EXECUTED** as of
2026-11-09. It creates no executable guest fixture and offers no positive
capability claim.

## Required scenarios (all five backends)

| Case | Required independently observed behavior |
| --- | --- |
| `uhl1-old-turn-physical-pin` | A previous code generation remains callable until the last real source Task, suspension, callback, FFI/table loan, and mailbox turn have physically retired; no early image release |
| `uhl1-failed-stage-keeps-route` | Compiler-validated candidate fails startup or health check; the serving actor route, supervisor lineage, identities and state remain on the prior generation; rollback must be observable |
| `uhl1-stale-or-forged-denial` | Compiled negative guest attempt with stale/foreign generation, forged authority or invalid ABI is denied with exact diagnostic and no published image/route |
| `uhl1-compiled-full-aot-handoff` | **Actual compiler-produced** guest, exactly pinned to source SHA/tree and artifact SHA-256, executes an AOT-compatible release handoff without unsupported dynamic loading |

The final case is **backend-specific**, not a demand for illicit JIT or
Assembly.Load: Java must use a real GraalVM Native Image deployment path;
LLVM must load an authenticated AOT/PIC image and independently identify
ORC JIT if used; Wasm must run a compiled Wasm guest under an admitted
host/broker; Forge must execute compiler-produced native actor guest
code; .NET NativeAOT must prove a statically rooted replacement with
a supervised rolling-process/image handoff, not runtime CIL/JIT.

## Classification and limitations

- `MISSING`: required case not implemented/reported.
- `NOT_EXECUTED`: authored test or suite has no stepful exact-head execution.
- `SKIPPED`: CI explicitly omitted the guest job; never a pass.
- `HOST_ONLY`: host protocol/model tests execute, but no compiled guest.
- `UNSUPPORTED_CONFIRMED`: negative capability query refuses a feature;
  honest fail-closed behavior, not positive hot-load parity.
- `GUEST_VERIFIED`: only after independent inspection of the actual runner
  attempt, compiler output, exact compiled image hash, guest test XML/logs,
  no skips, and observed generation/route/memory effects.

The checker currently accepts only `pass` on exact pinned TSV tuples,
and intentionally **does not authenticate submitted run IDs, SHA strings,
test counts or artifacts**. An internally consistent receipt is
`CONSISTENT_UNAUTHENTICATED_RECEIPT` only. A separate trusted read-only
GitHub verification of the **last attempt** must inspect checkout SHA,
tree, job steps, toolchain, compiler-produced fixture, artifact hashes
and raw guest execution outputs. Never copy a private repository into
another organization without explicit authorization.

## Smallest execution order

1. Run the current `cargo test --offline --locked` checker at this PR's
   exact head; report it as source-free parser/model evidence only.
2. Obtain an authorized, stepful Java/GraalVM source-local run for the
   transactional rollback and old-generation tests, then a real Native
   Image guest path. Missing/skipped JUnit XML is a hard hold.
3. Run the same four **compiler-produced** scenarios on native LLVM with
   exact-head compiler output and GCC/Clang ASan/UBSan lifecycle tests.
4. Attach independently corroborated Wasm, Forge and .NET (NativeAOT
   process handoff) guest receipts. `UNSUPPORTED` stays a gap.
5. Only after every case's trusted receipt exists may anyone consider
   positive five-backend claims. Do not reinterpret this manifest as a
   release gate or merge authorization.

Related: hot-loading-tests #1; Java #2351 and #1967; LLVM #602 and #607;
Wasm #256 and #245; .NET #319 and #308; common-source #206;
platform-conformance #291 and #570.
