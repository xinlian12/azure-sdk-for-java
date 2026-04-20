#!/usr/bin/env bash
# Copyright (c) Microsoft Corporation. All rights reserved.
# Licensed under the MIT License.
#
# Validates that the HDFS-dependent override files in azure-cosmos-spark_4-1_2-13
# stay in sync with the originals in azure-cosmos-spark_3/src/{main,test}/scala-hdfs.
# The files should be identical except for:
#   - The HDFSMetadataLog import line (streaming vs streaming.checkpointing)
#   - The 2-line header comment in the 4-1 overrides referencing the original file
#
# Run from the repo root or sdk/cosmos directory.
#
# TODO: Wire this script into CI — tracked in https://github.com/Azure/azure-sdk-for-java/issues/48881
# The cosmos-sdk-client.yml pipeline template does not currently support PreBuildSteps,
# and the emulator matrix runs on Windows agents where bash is unavailable. Options:
#   1. Add PreBuildSteps support to cosmos-sdk-client.yml and invoke from ci.yml
#   2. Rewrite as a platform-independent check (e.g., Maven Enforcer custom rule)
#   3. Add a dedicated Linux-based validation stage to ci.yml

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COSMOS_DIR="${SCRIPT_DIR}"

# If invoked from repo root, adjust
if [[ -d "${COSMOS_DIR}/sdk/cosmos" ]]; then
  COSMOS_DIR="${COSMOS_DIR}/sdk/cosmos"
fi

FILE_PAIRS=(
  "azure-cosmos-spark_3/src/main/scala-hdfs/com/azure/cosmos/spark/ChangeFeedInitialOffsetWriter.scala|azure-cosmos-spark_4-1_2-13/src/main/scala/com/azure/cosmos/spark/ChangeFeedInitialOffsetWriter.scala"
  "azure-cosmos-spark_3/src/main/scala-hdfs/com/azure/cosmos/spark/CosmosCatalogBase.scala|azure-cosmos-spark_4-1_2-13/src/main/scala/com/azure/cosmos/spark/CosmosCatalogBase.scala"
  "azure-cosmos-spark_3/src/test/scala-hdfs/com/azure/cosmos/spark/CosmosCatalogITestBase.scala|azure-cosmos-spark_4-1_2-13/src/test/scala/com/azure/cosmos/spark/CosmosCatalogITestBase.scala"
)

# Normalize a file by removing sync-related comments and replacing the
# checkpointing import with the old import for comparison purposes.
normalize() {
  grep -v '// This file mirrors azure-cosmos-spark_3' "$1" \
    | grep -v '// with HDFSMetadataLog import updated for SPARK-52787' \
    | grep -v '// NOTE: Override copy exists in azure-cosmos-spark_4-1_2-13' \
    | sed 's/org\.apache\.spark\.sql\.execution\.streaming\.checkpointing\.HDFSMetadataLog/org.apache.spark.sql.execution.streaming.HDFSMetadataLog/g'
}

FAILURES=0

for pair in "${FILE_PAIRS[@]}"; do
  IFS='|' read -r ORIGINAL OVERRIDE <<< "$pair"
  ORIG_PATH="${COSMOS_DIR}/${ORIGINAL}"
  OVER_PATH="${COSMOS_DIR}/${OVERRIDE}"

  if [[ ! -f "$ORIG_PATH" ]]; then
    echo "ERROR: Original file not found: ${ORIG_PATH}"
    FAILURES=$((FAILURES + 1))
    continue
  fi

  if [[ ! -f "$OVER_PATH" ]]; then
    echo "ERROR: Override file not found: ${OVER_PATH}"
    FAILURES=$((FAILURES + 1))
    continue
  fi

  DIFF_OUTPUT=$(diff <(normalize "$ORIG_PATH") <(normalize "$OVER_PATH") || true)

  if [[ -n "$DIFF_OUTPUT" ]]; then
    echo "DRIFT DETECTED between:"
    echo "  Original: ${ORIGINAL}"
    echo "  Override: ${OVERRIDE}"
    echo "  Diff (after normalizing HDFSMetadataLog import):"
    echo "$DIFF_OUTPUT"
    echo ""
    FAILURES=$((FAILURES + 1))
  fi
done

if [[ $FAILURES -gt 0 ]]; then
  echo "FAILED: ${FAILURES} file pair(s) have drifted. Update the 4-1 overrides to match."
  exit 1
else
  echo "OK: All HDFS override file pairs are in sync."
  exit 0
fi
