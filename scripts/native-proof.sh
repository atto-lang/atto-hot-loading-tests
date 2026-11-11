#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to GraalVM with matching Truffle components}"
"$JAVA_HOME/bin/native-image" --version
: "${ORESLANG_SOURCE_DIR:?Set ORESLANG_SOURCE_DIR to the clean pinned compiler checkout}"
bash "$root/scripts/test.sh"
mvn -B -ntp -f "$root/pom.xml" -DskipTests package dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt
cp_entries="$(cat "$root/target/runtime-classpath.txt")"
"$JAVA_HOME/bin/native-image" --no-fallback -O1 --parallelism=4 -J-Xmx6g \
  -H:+ReportExceptionStackTraces -Dgraalvm.locatorDisabled=true \
  -Dtruffle.UseFallbackRuntime=true -Dores.native.build-mode=aot \
  -Dores.native.ref-mode=compressed32 \
  --initialize-at-build-time=dev.oreslang.runtime.NativeBuildMode \
  '-H:IncludeResources=atto/.*' \
  -cp "$root/target/classes:$cp_entries" \
  dev.oreslang.hotreload.LiveUpgradeProof -o "$root/target/live-upgrade-aot"
"$root/target/live-upgrade-aot" native
