# Oreslang hot-loading conformance tests

This repository tests source/IR generation hot-loading, actor isolation, controlled upgrades, and eventual OTP-style supervision against the pinned `oreslang-source.java` runtime. The active executable baseline and proposed (not-yet-supported) runtime contracts are deliberately separated. See the draft pull request for the initial suite.

## Repository ownership and migration (2026-11-08)

**Public name:** AttoLang (formerly Oreslang). This remains `ores-truffle-oreslang/oreslang-hot-loading-tests` until a real authorized repo transfer. Preserve established `.ores` inputs and old tool/package names; do not claim feature parity from a branding or README change.

- **Scope:** generation-fenced hot-reload, quiescence, rollback, quarantine and actor ABI regression fixtures.
- **Not this repo:** production release orchestration or unverified backend certification.
- **Where code belongs:** [atto-lang](https://github.com/atto-lang) is the canonical home for specs, compilers, portable core/std, developer tooling, conformance and ecosystem examples. [atto-co](https://github.com/atto-co) separately governs deployment, identity, billing, tenant hosting and other managed infrastructure. Existing `atto-*` repos are not automatically transferred source identities.
- **Planned first-stage destination (not approved/executed):** `atto-lang/oreslang-hot-loading-tests`, under the same name and GitHub numeric repository ID. Direct legacy-org → `atto-co` transfers are not authorized in this wave; future product carve-outs require separate review.

**Migration checklist:** announce precise repo/head/rollback owner in [coordination issue #26](https://github.com/ores-truffle-oreslang/.github/issues/26); use private [migration plan #25](https://github.com/ores-truffle-oreslang/.github/pull/25). Obtain source/target admins and independent security signoff, verify target availability/protections/access, capture numeric repo ID and exact head SHA/tree, PRs/releases, Actions/runners/secrets, packages, deploy keys, GitHub Apps, webhooks, Pages and consumers. Never clone private source into a new org to bypass approval. Confirm stable identity/history/tree after authorized GitHub transfer and rerun exact-head CI at destination. **Zero-step jobs (`steps=[]` or `null`) are `NOT_EXECUTED`; host-only or source-free probes are not compiler-backed AttoLang guest receipts.** Move package/import references and names only after validated cutover.
