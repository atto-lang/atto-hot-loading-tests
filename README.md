# AttoLang live hot-upgrade conformance

The Java and Native Image runtime execute the same real `.atto` stateful actor
application in `src/main/resources/atto`. This reconciles draft suite #1 with
current source main and the transactional loader from Java draft #2351.
`SOURCE_REF` and `SOURCE_TREE_REF` explicitly pin the locally committed runtime
implementation; this is an intentional repin from the old October baseline.

The live test starts v1, sends its first mailbox message, and pauses the owning
request on a real loopback socket. After v2 publishes and serves a new request,
the old actor still owns its original state. Malformed source and a
compiler-admitted guest startup failure preserve the v2 serving route. A further
v2 request succeeds. Retirement while the old physical call is open fails even
when the caller has closed its outer route lease. Releasing I/O lets that same
old actor handle its second message. The guest observes exactly one teardown
output, terminal stream and actor done; only then can its context retire.
Closed-context execution and reuse of the released operation fail.

The test driver only controls socket barriers and checks guest results. Counts,
mailbox sequencing, actor state, network reads/closes and teardown run in guest
source. `1214` means v2 outputs 12 then 14; `102` means v1 outputs 1 then 2.

Run Java integration against the exact clean compiler checkout:

```sh
export JAVA_HOME=/path/to/graalvm
export ORESLANG_SOURCE_DIR=/path/to/clean/pinned/compiler
bash scripts/test.sh
```

Run the separate Native Image proof with matching Graal/Truffle components:

```sh
JAVA_HOME=/path/to/matching/graalvm bash scripts/native-proof.sh
```

Native Image builds an AOT runtime/interpreter and runs loaded guest source
without guest JIT. It does not establish closed-world guest machine-code image
replacement, native executable page unloading, LLVM/Wasm/Forge/.NET parity,
state migration, ABI admission, arbitrary detached-task lifetime tracking or OTP
supervision. `load`/`active` retain legacy staging behavior; production serving
uses transactional `reloadAndStart` and `pinServing`.

The legacy suite retains its negative async failure and all 8 actor fixtures,
updated to `get_ready()`/`get_done()`. Four previously disabled lifecycle gates
are now enabled. Three legacy-stage/ABI gates remain disabled. Two adversarial
context quota tests require an actual polyglot isolate library and are skipped
without it; these skips are not evidence of hostile isolation. Host coordinator
tests and Rust receipt inventories are not counted as guest execution proof.

See `docs/LIVE_UPGRADE_EXECUTION.md` for exact proof scope and commands. Required
origin merge checks remain required. No repository transfer, merge, release or
cross-organization token is part of this local proof.
