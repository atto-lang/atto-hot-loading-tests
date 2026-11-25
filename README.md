> **AI agent instructions (organization-wide):** Before working on code, issues, or pull requests, read and follow the [organization `AGENTS.md`](https://github.com/atto-lang/.github/blob/main/AGENTS.md), including its **merge-first stealth-development policy**. Honor any repository-local `AGENTS.md`, explicit holds, and GitHub-enforced rules.

# atto-hot-loading-tests

Conformance and regression tests for AttoLang dynamic loading and hot-reloading behavior.

## Naming migration and language authority

This repository is maintained at [atto-lang/atto-hot-loading-tests](https://github.com/atto-lang/atto-hot-loading-tests); use `https://github.com/atto-lang/atto-hot-loading-tests.git` for Git remotes and canonical `.atto` source examples. The normative language contract is [atto-spec](https://github.com/atto-lang/atto-spec), with executable guest evidence tracked by [atto-platform-conformance](https://github.com/atto-lang/atto-platform-conformance). Follow current ownership, borrowing, effects, actor, and syntax rules there; do not infer implemented features from a README or unexecuted CI.

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

AttoLang is the canonical project name; new source, examples and documentation use `.atto` and the `atto-lang` repository organization. This README defines scope, not feature readiness.

**Acceptance:** name exact source SHA/tree, runtime/toolchain, positive and negative checks and execution receipt before claiming support. Host-only tests and empty CI steps do not prove guest execution.

## Generation-fenced reload reference (host-only)

`reference/generation_inbox.py` provides a bounded mailbox/anti-replay model for host test fixtures: each message carries a generation and ticket; duplicate tickets are rejected even after delivery, reload invalidates old generations and pending messages, and close is terminal. Each generation has a finite ticket-memory budget. Run `python3 -m unittest discover -s tests -v` to exercise stale, replay, reload and closed cases.

This is **not** a verified Atto actor reload implementation. Real hot loading requires exact executable artifact identity, stop-the-world/lease quiescence, safe state migration, concurrent proof and host/guest execution receipts; this reference only defines observable admission behavior.

## Legacy provenance and transfer verification

The historical predecessor, `ores-truffle-oreslang/oreslang-hot-loading-tests`, is archived; the current `atto-lang/atto-hot-loading-tests` has a separate numeric GitHub repository ID. Preserve working legacy `.ores` fixtures and package/CLI aliases until their canonical `.atto` replacements are verified. The scope is generation-fenced hot-reload, quiescence, rollback, quarantine and actor ABI regression fixtures, not production release orchestration or unverified backend certification.

[atto-lang](https://github.com/atto-lang) owns language and portable tooling; [atto-co](https://github.com/atto-co) separately governs managed infrastructure, tenant hosting, identity and billing. Any future original-ID transfer or commercial carve-out requires separate owner authorization and independent security review. Inventory the exact SHA/tree, numeric ID, access, protections, PRs, releases, packages, runners, secrets, deploy keys, apps, webhooks, Pages, consumers and rollback ownership; verify identity and retest after any authorized transfer. Never mirror private source without authorization. Zero-step jobs are NOT_EXECUTED; host-only tests do not establish compiled guest execution.
