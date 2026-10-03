#!/usr/bin/env bash
# Run from Git Bash on Windows, or Bash on macOS/Linux. Python uses only stdlib.
set -euo pipefail
export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ -n "${PYTHON:-}" ]]; then
  python_bin="$PYTHON"
elif command -v python3 >/dev/null 2>&1 && python3 -c 'import sys' 2>/dev/null; then
  python_bin=python3
else
  python_bin=python
fi
exec "$python_bin" "$script_dir/record_demo.py" "$@"
