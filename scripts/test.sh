#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_dir="${ORESLANG_SOURCE_DIR:-$root/../oreslang-source.java}"
if [[ ! -d "$source_dir/.git" ]]; then
  echo "ERROR: set ORESLANG_SOURCE_DIR to an authenticated local oreslang-source.java checkout." >&2
  exit 2
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: Maven is required." >&2
  exit 2
fi
expected="$(tr -d '[:space:]' < "$root/SOURCE_REF")"
actual="$(git -C "$source_dir" rev-parse HEAD)"
if [[ "$actual" != "$expected" ]]; then
  echo "ERROR: source checkout SHA mismatch. Expected $expected, got $actual." >&2
  exit 2
fi
if [[ -n "$(git -C "$source_dir" status --porcelain)" ]]; then
  echo "ERROR: source checkout has uncommitted changes; exact-source verification requires a clean tree." >&2
  exit 2
fi
mvn -q -f "$source_dir/pom.xml" -DskipTests install
mvn -B -f "$root/pom.xml" test
