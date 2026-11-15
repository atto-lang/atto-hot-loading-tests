# atto-hot-loading-tests

Conformance and regression tests for AttoLang dynamic loading and hot-reloading behavior.

## Purpose

Dynamic module updates, actor lifecycle and compatibility regression cases.

## Verification model

- Keep fixtures and expected results versioned with the repository.
- Track exact backend, toolchain, commit SHA, and executed test command.
- Report each feature as **passed**, **failed**, **unsupported**, or **not executed**; never infer guest execution from host-only tests.
- Use [AttoLang's language specification](https://github.com/atto-lang/atto-spec) as the portable semantic contract.

## Development

Use actual source and checked-in CI workflows to determine build and test commands. Changes should include positive, negative, and boundary cases appropriate to the tested behavior.

## Status

This README documents intended scope; current conformance must be established from executable fixtures and CI evidence.

## Related

- [AttoLang](https://github.com/atto-lang)
- [Platform conformance](https://github.com/atto-lang/atto-platform-conformance)

## Repository ownership and contribution routing

**Belongs here:** Conformance cases for module/actor hot-load, migration, quiescence, rollback, generation fencing and stale-reference rejection.

**Belongs elsewhere:** Hot-loading implementation belongs in respective atto-source.* backends; normative policy in atto-spec.

**Security and portability boundary:** Test concurrent live upgrades, cancellation and rollback under quotas; preserve stable ActorRef while rejecting stale generation and untrusted module capabilities.

AttoLang is the public name; preserve existing Oreslang/.ores compatibility and use canonical .atto for new portable library source. This README defines scope, not feature readiness.

**Acceptance:** name exact source SHA/tree, runtime/toolchain, positive and negative checks and execution receipt before claiming support. Host-only tests and empty CI steps do not prove guest execution.

## Generation-fenced reload reference (host-only)

`reference/generation_inbox.py` provides a bounded mailbox/anti-replay model for host test fixtures: each message carries a generation and ticket; duplicate tickets are rejected even after delivery, reload invalidates old generations and pending messages, and close is terminal. Each generation has a finite ticket-memory budget. Run `python3 -m unittest discover -s tests -v` to exercise stale, replay, reload and closed cases.

This is **not** a verified Atto actor reload implementation. Real hot loading requires exact executable artifact identity, stop-the-world/lease quiescence, safe state migration, concurrent proof and host/guest execution receipts; this reference only defines observable admission behavior.
