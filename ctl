#!/usr/bin/env bash
# ctl — the launcher (Java). No args → interactive menu; args → one-shot command.
# Succeeds the retired bash scripts/ctl.sh; builds the jar on first run.
set -euo pipefail

root="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
jar="$root/launcher/target/ctl.jar"

if [[ ! -f "$jar" ]]; then
  echo "ctl: building launcher (first run)…" >&2
  ( cd "$root/launcher" && mvn -q -B package -DskipTests )
fi

cd "$root"               # so the tool finds games/ + scripts/ regardless of CWD
exec java -jar "$jar" "$@"
