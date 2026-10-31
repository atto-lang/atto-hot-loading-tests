use std::process::{Command, Output};
use std::sync::atomic::{AtomicU64, Ordering};

const REPOSITORY: &str = "ores-truffle-oreslang/oreslang-hot-loading-tests";
const CASES: &str = include_str!("../cases.tsv");
static NEXT: AtomicU64 = AtomicU64::new(0);

struct Receipt(std::path::PathBuf);
impl Receipt {
    fn new(contents: &[u8]) -> Self {
        let path = std::env::temp_dir().join(format!(
            "ores-receipt-{}-{}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        std::fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&path)
            .and_then(|mut f| std::io::Write::write_all(&mut f, contents))
            .unwrap();
        Self(path)
    }
    fn inspect(&self) -> Output {
        cli()
            .arg(&self.0)
            .args(["1".repeat(40), "2".repeat(40), "3".repeat(64)])
            .output()
            .unwrap()
    }
}
impl Drop for Receipt {
    fn drop(&mut self) {
        let _ = std::fs::remove_file(&self.0);
    }
}
fn cli() -> Command {
    Command::new(env!("CARGO_BIN_EXE_ores-rollout-evidence"))
}

#[test]
fn cli_lists_exact_compiled_profile() {
    let output = cli().arg("--cases").output().unwrap();
    assert!(output.status.success());
    assert_eq!(output.stdout, CASES.as_bytes());
}

#[test]
fn cli_usage_and_missing_input_are_nonzero() {
    assert!(!cli().output().unwrap().status.success());
    let absent = Receipt::new(b"");
    std::fs::remove_file(&absent.0).unwrap();
    assert!(!absent.inspect().status.success());
}

#[test]
fn cli_rejects_oversized_and_invalid_utf8() {
    for bytes in [vec![b'x'; 1024 * 1024 + 1], vec![0xff]] {
        let output = Receipt::new(&bytes).inspect();
        assert!(!output.status.success());
        assert!(output.stderr.starts_with(b"REJECTED:"));
    }
}

#[test]
fn cli_accepts_only_consistent_synthetic_metadata() {
    let mut text = String::from("repository\tsuite\tbackend\tphase\toutcome\tcommit\ttree\tcorpus_sha256\trun_id\tjob_id\tsteps\ttests\texit_code\tartifact_sha256\n");
    for row in CASES.lines().filter(|row| !row.starts_with('#')) {
        text.push_str(&format!(
            "{REPOSITORY}\t{row}\tpass\t{}\t{}\t{}\t1\t2\t3\t4\t0\t{}\n",
            "1".repeat(40),
            "2".repeat(40),
            "3".repeat(64),
            "4".repeat(64)
        ));
    }
    let output = Receipt::new(text.as_bytes()).inspect();
    assert!(output.status.success(), "{:?}", output.stderr);
    assert!(output
        .stdout
        .starts_with(b"CONSISTENT_UNAUTHENTICATED_RECEIPT"));
    let stale = text.replace(&"1".repeat(40), &"5".repeat(40));
    assert!(!Receipt::new(stale.as_bytes()).inspect().status.success());
    let skipped = text.replace("\tpass\t", "\tskipped\t");
    assert!(!Receipt::new(skipped.as_bytes()).inspect().status.success());
}

#[test]
fn cli_rejects_directory_before_bounded_read() {
    let output = cli()
        .arg(std::env::temp_dir())
        .args(["1".repeat(40), "2".repeat(40), "3".repeat(64)])
        .output()
        .unwrap();
    assert!(!output.status.success());
    assert!(String::from_utf8_lossy(&output.stderr).contains("regular non-symlink file"));
}

#[cfg(unix)]
#[test]
fn cli_rejects_symlink_to_receipt_before_open() {
    use std::os::unix::fs::symlink;
    let target = Receipt::new(b"does-not-matter");
    let link = std::env::temp_dir().join(format!(
        "ores-receipt-link-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    symlink(&target.0, &link).unwrap();
    let output = cli()
        .arg(&link)
        .args(["1".repeat(40), "2".repeat(40), "3".repeat(64)])
        .output()
        .unwrap();
    std::fs::remove_file(&link).unwrap();
    assert!(!output.status.success());
    assert!(String::from_utf8_lossy(&output.stderr).contains("regular non-symlink file"));
}

#[cfg(target_os = "linux")]
#[test]
fn cli_rejects_named_fifo_without_opening_or_blocking() {
    let fifo = std::env::temp_dir().join(format!(
        "ores-receipt-fifo-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    assert!(Command::new("mkfifo").arg(&fifo).status().unwrap().success());
    let output = cli()
        .arg(&fifo)
        .args(["1".repeat(40), "2".repeat(40), "3".repeat(64)])
        .output()
        .unwrap();
    std::fs::remove_file(&fifo).unwrap();
    assert!(!output.status.success());
    assert!(String::from_utf8_lossy(&output.stderr).contains("regular non-symlink file"));
}

#[test]
fn cli_rejects_receipt_without_final_lf() {
    let output = Receipt::new(b"repository\tsuite\tbackend\tphase").inspect();
    assert!(!output.status.success());
}
