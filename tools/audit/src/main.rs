//! Self-contained (std-only) fail-closed fixture and design-contract audit.
//! This never substitutes for compiling/running the pinned GraalVM runtime.

use std::collections::BTreeSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::ExitCode;

const FIXTURES: &[(&str, &str)] = &[
    ("isolated-counter-v1.ores", "define isoactor Counter as"),
    ("isolated-counter-v2.ores", "define isoactor Counter as"),
    ("shared-counters.ores", "define actor Counter as"),
    ("untrusted-actor.ores", "define untrusted actor Worker as"),
    ("crash-on-start.ores", "val invalid = numbers[99];"),
    ("actor-async-crash.ores", "define isoactor Counter as"),
];

fn read(root: &Path, path: &str) -> Result<String, String> {
    fs::read_to_string(root.join(path))
        .map_err(|err| format!("{path}: {err}"))
}

fn validate_hex_pin(label: &str, contents: &str) -> Result<(), String> {
    let pin = contents.trim();
    if pin.len() != 40 || !pin.bytes().all(|byte| byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase()) {
        return Err(format!("{label} must be a single 40-character lowercase SHA-1 hex digest"));
    }
    Ok(())
}

fn matrix_numbers(matrix: &str) -> Result<BTreeSet<u32>, String> {
    let mut seen = BTreeSet::new();
    for line in matrix.lines() {
        let Some(rest) = line.trim().strip_prefix("| H") else {
            continue;
        };
        let Some((raw, _)) = rest.split_once(" |") else {
            return Err(format!("malformed matrix row: {line}"));
        };
        let id: u32 = raw.parse()
            .map_err(|_| format!("invalid matrix number in row: {line}"))?;
        if !seen.insert(id) {
            return Err(format!("duplicate matrix case H{id:02}"));
        }
    }
    Ok(seen)
}

fn check(root: &Path) -> Result<(), String> {
    validate_hex_pin("SOURCE_REF", &read(root, "SOURCE_REF")?)?;
    validate_hex_pin("SOURCE_TREE_REF", &read(root, "SOURCE_TREE_REF")?)?;
    let runner = read(root, "scripts/test.sh")?;
    for required in ["HEAD^{tree}", "SOURCE_TREE_REF", "status --porcelain", "mvn -B -ntp"] {
        if !runner.contains(required) {
            return Err(format!("test runner missing source/verification guard: {required}"));
        }
    }

    let docs = read(root, "docs/OTP_UPGRADE_MATRIX.md")?;
    let numbers = matrix_numbers(&docs)?;
    let expected: BTreeSet<u32> = (1..=30).collect();
    if numbers != expected {
        return Err(format!("OTP matrix must include unique contiguous H01..H30; found: {numbers:?}"));
    }
    if !docs.contains("Pending") {
        return Err("OTP matrix must document pending implementation status".to_string());
    }

    for &(fixture, expected_snippet) in FIXTURES {
        let path = format!("src/test/resources/ores/{fixture}");
        let source = read(root, &path)?;
        if !source.contains(expected_snippet) {
            return Err(format!("{path}: required conformance construct missing"));
        }
        if !source.contains("pub ") || !source.contains("routine main()") {
            return Err(format!("{path}: missing executable guest entrypoint"));
        }
    }

    let classes = [
        ("HotLoadingBaselineTest.java", 16),
        ("HotLoadingActorTest.java", 7),
        ("HostReleaseCoordinatorTest.java", 8),
        ("PendingRuntimeContractTest.java", 6),
    ];
    for &(file, min_cases) in &classes {
        let source = read(root, &format!("src/test/java/dev/oreslang/hotreload/{file}"))?;
        let cases = source.matches("@Test").count();
        if cases < min_cases {
            return Err(format!("{file}: expected at least {min_cases} @Test cases; found {cases}"));
        }
        if file == "PendingRuntimeContractTest.java" {
            let disabled = source.matches("@Disabled(").count();
            if disabled != cases {
                return Err(format!("{file}: every pending case must be @Disabled ({disabled} of {cases})"));
            }
        } else if source.contains("@Disabled") {
            return Err(format!("{file}: executable test class must not contain @Disabled"));
        }
    }
    Ok(())
}

fn main() -> ExitCode {
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../..");
    match check(&root) {
        Ok(()) => {
            println!("PASS: pinned source verification guards, OTP matrix, guest fixtures, and JUnit inventories");
            ExitCode::SUCCESS
        }
        Err(err) => {
            eprintln!("FAIL: {err}");
            ExitCode::FAILURE
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_duplicate_scenario_numbers() {
        assert!(matrix_numbers("| H01 | a |\n| H01 | b |").is_err());
    }
    #[test]
    fn parses_matrix_rows_without_confusing_headings() {
        let found = matrix_numbers("# H01\n| H01 | a |\n| H02 | b |").unwrap();
        assert_eq!(found, BTreeSet::from([1, 2]));
    }
    #[test]
    fn rejects_invalid_commit_pins() {
        assert!(validate_hex_pin("bad", "not-a-sha").is_err());
        assert!(validate_hex_pin("good", &"a".repeat(40)).is_ok());
    }
}
