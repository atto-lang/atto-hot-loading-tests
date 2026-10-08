#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
source_dir="${ORESLANG_SOURCE_DIR:-$root/../oreslang-source.java}"

if [[ ! -d "$source_dir" ]]; then
  echo "ERROR: ORESLANG_SOURCE_DIR must name an authenticated source checkout." >&2
  exit 2
fi
if ! git -C "$source_dir" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: source directory is not a Git working tree." >&2
  exit 2
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: Maven is required." >&2
  exit 2
fi
if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java is required." >&2
  exit 2
fi

source_dir="$(cd "$source_dir" && pwd -P)"
git_root="$(git -C "$source_dir" rev-parse --show-toplevel)"
if [[ "$source_dir" != "$git_root" ]]; then
  echo "ERROR: source checkout must point at the Git repository root." >&2
  exit 2
fi
if [[ "$source_dir" == "$root" ]]; then
  echo "ERROR: source checkout must be distinct from the harness checkout." >&2
  exit 2
fi

read -r expected_commit < "$root/SOURCE_REF"
read -r expected_tree < "$root/SOURCE_TREE_REF"
actual_commit="$(git -C "$source_dir" rev-parse HEAD)"
actual_tree="$(git -C "$source_dir" rev-parse 'HEAD^{tree}')"

# Tree identity is the admission authority: a cross-repository mirror has a
# different commit SHA, but must retain *exactly* the pinned source Git tree.
if [[ "$actual_tree" != "$expected_tree" ]]; then
  echo "ERROR: source Git tree mismatch. Expected $expected_tree; got $actual_tree." >&2
  exit 2
fi
if [[ -n "$(git -C "$source_dir" status --porcelain --untracked-files=all)" ]]; then
  echo "ERROR: source checkout contains local modifications/untracked files." >&2
  exit 2
fi
if [[ "$actual_commit" != "$expected_commit" ]]; then
  echo "Source mirror accepted: commit identity differs but pinned tree identity matches."
fi

echo "Source verification passed: commit=$actual_commit tree=$actual_tree"
mvn -B -ntp -f "$source_dir/pom.xml" -DskipTests install
mvn -B -ntp -f "$root/pom.xml" verify

if [[ -n "$(git -C "$source_dir" status --porcelain --untracked-files=all)" ]]; then
  echo "ERROR: source tree was modified during Maven verification." >&2
  exit 2
fi
echo "Hot-loading integration suite completed against pinned source tree."
