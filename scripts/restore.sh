#!/usr/bin/env bash
# restore.sh — restore a world snapshot (local or offsite).
#
#   ./scripts/restore.sh minecraft               # list available snapshots
#   ./scripts/restore.sh minecraft latest        # restore newest local snapshot
#   ./scripts/restore.sh minecraft minecraft-20260616-1400.tar.gz
#
# Stops the server first, swaps the data dir (old one moved aside, not deleted),
# then you bring the server back up.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Load .env safely — values can contain spaces/quotes (e.g. Gabriel's ARK),
# so parse KEY=VALUE rather than `source` it as bash.
if [[ -f "$ROOT/.env" ]]; then
  while IFS='=' read -r _k _v; do
    [[ "$_k" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] && export "$_k=$_v"
  done < "$ROOT/.env"
fi

game="${1:?Specify a game: minecraft | palworld | ark-se}"
choice="${2:-}"
dest_dir="$ROOT/backups/$game"

case "$game" in
  minecraft) data="$ROOT/games/minecraft/data" ;;
  palworld)  data="$ROOT/games/palworld/data" ;;
  ark-se)    data="$ROOT/games/ark-se/server" ;;
  *) echo "✗ Unknown game '$game'" >&2; exit 1 ;;
esac

if [[ -z "$choice" ]]; then
  echo "Local snapshots for $game:"
  # shellcheck disable=SC2011,SC2012
  ls -1t "$dest_dir"/*.tar.gz 2>/dev/null | xargs -r -n1 basename || echo "  (none)"
  [[ -n "${RCLONE_REMOTE:-}" ]] && { echo "Offsite ($RCLONE_REMOTE/$game/):"; rclone lsf "$RCLONE_REMOTE/$game/" 2>/dev/null || true; }
  echo; echo "Re-run with a filename or 'latest' to restore."
  exit 0
fi

if [[ "$choice" == "latest" ]]; then
  # shellcheck disable=SC2012
  archive="$(ls -1t "$dest_dir"/*.tar.gz 2>/dev/null | head -1)"
  [[ -n "$archive" ]] || { echo "✗ No local snapshots found" >&2; exit 1; }
else
  archive="$dest_dir/$choice"
  if [[ ! -f "$archive" && -n "${RCLONE_REMOTE:-}" ]]; then
    echo "→ fetching $choice from offsite…"
    rclone copy "$RCLONE_REMOTE/$game/$choice" "$dest_dir/" --no-traverse
  fi
  [[ -f "$archive" ]] || { echo "✗ Snapshot not found: $choice" >&2; exit 1; }
fi

echo "⚠  About to restore: $(basename "$archive")"
echo "   Current world at $data will be moved to ${data}.bak-$(date +%s)"
read -r -p "   Proceed? [y/N] " ok; [[ "$ok" == "y" || "$ok" == "Y" ]] || exit 0

"$ROOT/scripts/ctl.sh" down "$game" || true
[[ -d "$data" ]] && mv "$data" "${data}.bak-$(date +%s)"
mkdir -p "$(dirname "$data")"
tar -xzf "$archive" -C "$(dirname "$data")"
echo "✓ Restored. Bring it back up with: ./scripts/ctl.sh up $game"
