# OTP-style code upgrades: test contract, not a claim of implementation

Status: **baseline generational hot loader exists; OTP release handling and runtime-owned supervisors are not established by this suite**. The executable Java tests exercise `HotReloadManager` at the pinned source commit; the cases in the following matrix are specifications for future runtime integration tests.

## Design invariants

- **Static declarations per generation.** Class, actor, module, trait, and ABI identities are compiler-produced and immutable inside each loaded generation. JVM/GraalVM JIT optimizes code but does *not* permit runtime class creation or weakening of the type checker. New code is parsed, type-checked, capability-checked, and linked before any route is switched.
- **Release isolation.** A process may retain two or more code generations at once. An actor has a fixed code-generation identity through one mailbox turn; old in-flight requests finish (or are explicitly cancelled) before retirement. Private `isoactor` generations use separate state/context. Shared actors cannot acquire illicit mutable pointers to an earlier/later generation. Code images may be immutable/shared; actor state may not.
- **Supervisor-owned activation.** Staging, health-checking, routing cutover, draining, and retirement form a transaction-like protocol. Failed validation or startup **must not move the active route pointer**. A failed canary rolls back to the last known-good version. `HotReloadManager.load()` currently updates `active()` during staging: production routing must NOT trust that pointer until atomic promotion is implemented.
- **State is never implicitly reinterpreted.** Explicit `upgrade(oldVersion, state) -> Result<NewState, Error>` migrations run under bounded time/memory and typed schemas, with rollback strategy; default is stop/restart with initial state. Migration must be version-aware, deterministic, idempotent where retried, and never pass actor-owned heap addresses or active capability handles between generations.
- **No dynamic host escape hatch.** Source/IR loading works in JIT, interpreter/AOT, and supported hybrid profiles. No JVM class loaders, JNI arbitrary libraries, `eval` types, or reflection are required for semantics.
- **Backpressure and safety.** Mailboxes remain bounded across upgrade; no message loss or replay without explicit policy. In-flight futures, channel subscriptions, outputs, timers, child actors, cancellation and process monitors must have a deterministic ownership/teardown policy.
- **Failure containment.** A broken child does not kill a supervisor or sibling; repeated failures trip bounded restart intensity and backoff. Supervisor itself can be replaced only through a higher stable supervisor/router boundary, never by replacing its own running stack/code in place.

## Erlang/OTP parity targets (pending runtime APIs)

| ID | Scenario | Expected invariant | Status |
| --- | --- | --- | --- |
| H01 | Isolated actor v1 → v2; old requests still running | New sends use v2 after atomic cutover; old requests complete on v1 | Pending |
| H02 | Upgrade shared actor with writable shared cells | No cross-generation mutable alias; explicit migration or rejection | Pending |
| H03 | Load invalid code | Remains on v1; no leaked contexts, references, or reservation | Partially executable |
| H04 | Startup or health-check failure after staging | Roll back route, keep v1 healthy, dispose failed v2 | Pending |
| H05 | Load v1 → v2 → v3 concurrently | Total order of activation; stale candidate cannot supersede latest | Pending |
| H06 | Two-version retention quota / purge | Bounded memory; retirement only after old traffic drain | Pending |
| H07 | Changed message type / actor ABI | Reject before promotion unless explicit versioned adapter exists | Pending |
| H08 | Changed state layout | Require explicit typed migration or fresh restart | Pending |
| H09 | Migrator crashes / times out / OOMs | Abort atomically, no partially exposed migrated state | Pending |
| H10 | In-flight future/channel/output on old generation | Ownership, completion and cancellation are deterministic | Pending |
| H11 | Supervisor one_for_one | Only failing child restarted; siblings unaffected | Pending |
| H12 | Supervisor one_for_all | Deterministic child-group restart order | Pending |
| H13 | Supervisor rest_for_one | Only later siblings restarted in defined order | Pending |
| H14 | Restart modes: permanent / transient / temporary | Exit reasons govern restart eligibility | Pending |
| H15 | Restart intensity + time window | Escalate to parent; bounded retries/backoff/jitter | Pending |
| H16 | Actor links and monitors | Traps/exits and DOWN reports delivered once, with reason | Pending |
| H17 | Ordered child init / terminate | Start dependencies first; stop children reverse order | Pending |
| H18 | Upgrade supervisor specification itself | Parent owns handoff; old subtree drains exactly once | Pending |
| H19 | Hot upgrade supervisor while child upgrade fails | No split-brain supervisor and no mixed unvalidated child graph | Pending |
| H20 | Distribution: remote actor references and node disconnects | Fencing epochs; stale refs fail closed, no accidental replay | Pending |
| H21 | Rolling deploy under hostile source + capability change | Capabilities never increase without explicit parent authorization | Pending |
| H22 | JIT vs AOT vs hybrid | Equal source semantics; JIT only changes optimizations | Pending |
| H23 | Mailbox pressure during cutover | Bounded queue, explicit overload response, preserved per-actor FIFO | Pending |
| H24 | N concurrent upgrades | Resource budgets and lock ordering prevent starvation/deadlock | Pending |
| H25 | Rollback after v2 already accepted writes | Versioned external effects, fencing/idempotency; no fake automatic undo | Pending |
| H26 | Restart after supervisor crash | State restore or safe cold start; no orphaned children | Pending |
| H27 | Blue/green generations with sticky sessions | Requests stick to one actor generation until completed | Pending |
| H28 | Repeated upgrades / resource leak soak | Contexts, class images, threads, file descriptors, timers reclaim | Pending |
| H29 | Upgrade notifications and observer streams | Sequenced, bounded, typed lifecycle events | Pending |
| H30 | Typed actor request/reply through upgrade | Correlation, cancellation, duplicate reply and ABI admission | Pending |

## Suggested runtime API (proposal only)

```text
SupervisorRef.install(candidate : CodeBundle, expected_epoch: Epoch) -> Result<StagedId, AdmissionError>
SupervisorRef.verify(staged : StagedId, probes : ProbeSet) -> Result<ReadyId, HealthError>
SupervisorRef.promote(ready : ReadyId, expected_epoch: Epoch) -> Result<Epoch, Conflict>
SupervisorRef.retire(generation : GenerationId, strategy : DrainPolicy) -> Future<Retirement>
SupervisorRef.rollback(expected_epoch : Epoch, to : HealthyGeneration) -> Result<Epoch, Conflict>
```

These are **proposals**, not existing Oreslang or Java APIs. Keep the supervisor control plane on a stable trusted generation; admission rights and state migration authority must not be inherited from guest actor code.

## Ordering and rollout

1. Add atomic staging vs activation state and a route epoch with compare-and-swap; make failed start close and unstage.
2. Implement per-generation actor affinity, reliable draining, and retirement accounting.
3. Define static typed ABI comparison and migration interfaces (start with isolated actors).
4. Add runtime-owned supervisor tree with monitor/exit/restart policies and bounded intensity.
5. Introduce supervisor-spec upgrades through stable parent and expand to shared actors and multi-node fencing.
6. Require randomized scheduling, failure injection, high-load soak, and AOT parity before declaring OTP equivalence.

Do **not** report OTP equivalence based on baseline tests alone. Erlang also has distributed process semantics, release handlers, code-change callbacks, monitors, links, and supervision/restart policies; each must have executable evidence.
