# Live upgrade proof scope

Guest implementation: `counter-v1.atto` and `counter-v2.atto` own actor state,
two mailbox operations, network synchronization and teardown. The actor remains
live and stateful between mailbox turns while its owning request awaits I/O.
The physical managed-call pin remains held through VM root-task completion,
including actor done and socket close. Retirement rejects while the call is
open; the supervisor retries it after drain. There is no forced retirement.

Java exceptions: `LiveUpgradeProof` is a test driver, not a replacement actor
application. It controls loopback socket release, performs loader operations and
asserts scalar results. Source runtime changes implement privileged serving-route
publication, Graal embedding entry and physical-call leases; the new root command
reuses the existing async scheduler rather than implementing guest behavior.

The source pin is deliberately advanced from draft #1's October source to current
main plus an isolated reconciliation of Java #2351. Current source-binding and
CodeImageIdentity defenses are retained. The original source entry/close race
reproduced in source regressions and is fixed by a private scoped host-entry
permit. All 53 selected compiler regression cases and 17 actor teardown cases
were executed with no failures after the fix. Final commands/logs and binary
hashes are recorded in the external task receipt.

Native qualification uses a real arm64 Mach-O Native Image executable with
BUILD_KIND=AOT. It runs the same guest source and barriers, not a JVM subprocess.
The runtime/interpreter is ahead-of-time compiled; guest source parsing and
interpretation occur inside that executable. Full guest machine-code AOT
handoff, image page unloading and the broader five-backend UHL manifest in draft
#9 remain unverified and are not inferred from this run.

An exploratory receive-side socket suspension plus self.end omitted its teardown
output on current main: ActorRuntime's graceful-stop gate excludes suspended
receives. That broader async-receive teardown behavior is not claimed fixed.
This application keeps I/O in the owning guest request; its actor receives are
synchronous, retain state across cutover and finish with verified teardown.

Future work remains in the 45-case OTP matrix. The matrix's pending entries are
not passing tests. In particular this proof does not migrate state into a new
actor schema or replace the code identity of an already executing actor.
