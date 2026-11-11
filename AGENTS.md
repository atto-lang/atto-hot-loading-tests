# Local repository guidance

This repository is a cross-repository conformance suite for the Oreslang runtime.
Do not hide unsupported features behind simulated/fake passes. Separate executable
tests from design-only test plans, and require an exact pinned source runtime.
Do not silently upgrade SOURCE_REF.

## Shared policy

Follow ORESoftware/my-ai AGENTS.md and SHARED.md. On developer machines
`.ores/agents/AGENTS.md` is a *local untracked symlink* to SHARED.md and
must not be committed into this repository. Do not copy shared-policy secrets.
