
#!/usr/bin/env bash
set -euo pipefail

# ============================================
# ByteGraph Runner (macOS/Linux .sh)
# Usage: ./run-bytegraph.sh /absolute/path/to/MyClass.class [JAVA8_HOME]
#  - arg1 (required): absolute path to the .class file to analyze
#  - arg2 (optional): JDK 8 home (e.g., /Library/Java/JavaVirtualMachines/jdk1.8.0_202/Contents/Home)
# ============================================

# 0) Check arguments
if [[ $# -lt 1 ]]; then
  echo "[ERROR] .class file path is required."
  echo "Usage: $0 /absolute/path/to/MyClass.class [JAVA8_HOME]"
  exit 1
fi

CLASS_PATH="$1"

# 1) Set JAVA8_HOME (arg2 overrides existing env var)
if [[ $# -ge 2 ]]; then
  export JAVA8_HOME="$2"
fi

if [[ -z "${JAVA8_HOME:-}" ]]; then
  echo "[ERROR] JAVA8_HOME is not set."
  echo "Example: export JAVA8_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202/Contents/Home"
  exit 2
fi

# 2) Locate rt.jar (try both common locations)
RT1="$JAVA8_HOME/jre/lib/rt.jar"
RT2="$JAVA8_HOME/lib/rt.jar"
RTJAR=""
if [[ -f "$RT1" ]]; then
  RTJAR="$RT1"
elif [[ -f "$RT2" ]]; then
  RTJAR="$RT2"
fi

if [[ -z "$RTJAR" ]]; then
  echo "[ERROR] rt.jar not found."
  echo "Checked:"
  echo "  $RT1"
  echo "  $RT2"
  echo "Ensure JAVA8_HOME points to a valid JDK 8 root."
  exit 3
fi

echo "[INFO] JAVA8_HOME=$JAVA8_HOME"
echo "[INFO] rt.jar=$RTJAR"

# 3) Run Gradle (must be executed in project root)
echo "[INFO] Running Gradle..."
gradle run --args="$CLASS_PATH"

echo "[INFO] Done. Check the out/ folder for the generated JSON."
