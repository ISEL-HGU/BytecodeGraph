#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# ByteGraph Runner (macOS/Linux)
# Usage: ./run-bytegraph.sh <classpath_root> [mode]
#
# - arg1 (required): Absolute path to the class or directory
# - arg2 (optional): Analysis mode (SEMANTIC or DETAILED)
# ============================================================

# 0) Check if the required argument is provided [2]
if [[ $# -lt 1 ]]; then
    echo "[ERROR] Target class path is required."
    echo "Usage: $0 /absolute/path/to/your/classes [mode]"
    exit 1
fi

CLASS_PATH="$1"
# Default mode to SEMANTIC if the second argument is missing [3]
MODE="${2:-SEMANTIC}"

echo "[INFO] Initializing ByteGraph Analysis..."
echo "[INFO] Class Path: $CLASS_PATH"
echo "[INFO] Mode: $MODE"

# 1) Execute Gradle task [4]
# Added -Dfile.encoding=UTF-8 to prevent character encoding issues [1].
# The --args will be passed to Main.java [1, 4].
gradle run --args="$CLASS_PATH $MODE" -Dfile.encoding=UTF-8

echo "[INFO] Done. Please check the 'out/' directory for JSON results."