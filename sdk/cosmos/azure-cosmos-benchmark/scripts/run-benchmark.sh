#!/bin/bash
# run-benchmark.sh — Run a multi-tenancy benchmark scenario with auto-detected git metadata
#
# Usage:
#   ./run-benchmark.sh <scenario> <tenants-file> [output-dir] [--branch <branch>] [--pr <number>] [--result-sink <CSV|COSMOS|KUSTO|ALL>]

set -euo pipefail

SCENARIO=${1:-SCALING}
TENANTS_FILE=${2:-tenants.json}
OUTPUT_DIR=${3:-./results/$(date +%Y%m%dT%H%M%S)-${SCENARIO}}
BRANCH=""
PR_NUMBER=""
RESULT_SINK="CSV"
EXTRA_ARGS=""

shift 3 2>/dev/null || true
while [[ $# -gt 0 ]]; do
    case $1 in
        --branch)      BRANCH="$2"; shift ;;
        --pr)          PR_NUMBER="$2"; shift ;;
        --result-sink) RESULT_SINK="$2"; shift ;;
        *)             EXTRA_ARGS="$EXTRA_ARGS $1" ;;
    esac
    shift
done

mkdir -p "$OUTPUT_DIR"

# Auto-detect git metadata if not provided
if [[ -z "$BRANCH" ]]; then
    BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
fi
COMMIT_ID=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
COMMIT_MSG=$(git log -1 --pretty=%s 2>/dev/null || echo "")

echo "=== Multi-Tenancy Benchmark ==="
echo "  Scenario:  $SCENARIO"
echo "  Tenants:   $TENANTS_FILE"
echo "  Branch:    $BRANCH"
echo "  Commit:    $COMMIT_ID"
echo "  PR:        ${PR_NUMBER:-none}"
echo "  Sink:      $RESULT_SINK"
echo "  Output:    $OUTPUT_DIR"

# Save git metadata
cat > "${OUTPUT_DIR}/git-info.json" <<EOF
{
    "branch": "$BRANCH",
    "commitId": "$COMMIT_ID",
    "commitMessage": "$(echo "$COMMIT_MSG" | sed 's/"/\\"/g')",
    "prNumber": "${PR_NUMBER:-null}",
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

# JVM flags
JVM_OPTS="-Xmx8g -Xms8g \
  -XX:+UseG1GC \
  -XX:MaxDirectMemorySize=2g \
  -Xlog:gc*:file=${OUTPUT_DIR}/gc.log:time,uptime,level \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=${OUTPUT_DIR}/"

# Find the benchmark JAR
BENCHMARK_JAR=$(find sdk/cosmos/azure-cosmos-benchmark/target -name "azure-cosmos-benchmark-*-jar-with-dependencies.jar" 2>/dev/null | head -1)
if [[ -z "$BENCHMARK_JAR" ]]; then
    echo "ERROR: Benchmark JAR not found. Build first:"
    echo "  mvn package -pl sdk/cosmos/azure-cosmos-benchmark -DskipTests"
    exit 1
fi

# Run
java $JVM_OPTS \
  -cp "$BENCHMARK_JAR" \
  com.azure.cosmos.benchmark.MultiTenancyBenchmark \
  --tenantsFile "$TENANTS_FILE" \
  --scenario "$SCENARIO" \
  --outputDir "$OUTPUT_DIR" \
  --branch "$BRANCH" \
  --commitId "$COMMIT_ID" \
  ${PR_NUMBER:+--prNumber "$PR_NUMBER"} \
  --resultSink "$RESULT_SINK" \
  --monitorIntervalSec 10 \
  $EXTRA_ARGS \
  2>&1 | tee "${OUTPUT_DIR}/benchmark.log"

# Collect system metrics snapshot (Linux only)
ss -s > "${OUTPUT_DIR}/ss-summary.txt" 2>/dev/null || true
cat /proc/self/status > "${OUTPUT_DIR}/proc-status.txt" 2>/dev/null || true

echo "Results in: $OUTPUT_DIR"
