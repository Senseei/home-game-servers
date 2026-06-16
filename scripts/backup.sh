#!/usr/bin/env bash
# backup.sh — flush the world via RCON, snapshot it, rotate, and push offsite.
#
#   ./scripts/backup.sh minecraft
#   ./scripts/backup.sh palworld
#   ./scripts/backup.sh ark-se
#
# Designed to be safe to run while players are online: it tells the server to
# flush its save to disk first, so the tarball never captures a torn world.
# Wire it to a systemd timer or cron (see README) for hands-off protection.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
[[ -f "$ROOT/.env" ]] && set -a && . "$ROOT/.env" && set +a

BACKUP_KEEP_LOCAL="${BACKUP_KEEP_LOCAL:-7}"
BACKUP_KEEP_REMOTE="${BACKUP_KEEP_REMOTE:-14}"
RCLONE_REMOTE="${RCLONE_REMOTE:-}"

game="${1:?Specify a game: minecraft | palworld | ark-se}"
stamp="$(date +%Y%m%d-%H%M%S)"
dest_dir="$ROOT/backups/$game"
mkdir -p "$dest_dir"

# Per-game: container name, the directory to snapshot, and how to flush its save.
case "$game" in
  minecraft)
    container="mc"; data="$ROOT/games/minecraft/data"
    flush_off() { docker exec "$container" rcon-cli save-off >/dev/null && docker exec "$container" rcon-cli save-all flush >/dev/null; }
    flush_on()  { docker exec "$container" rcon-cli save-on  >/dev/null; }
    ;;
  palworld)
    container="palworld-server"; data="$ROOT/games/palworld/data"
    flush_off() { docker exec "$container" rcon-cli "Save" >/dev/null; }
    flush_on()  { :; }
    ;;
  ark-se)
    container="ark-server"; data="$ROOT/games/ark-se/server"
    flush_off() { docker exec "$container" rcon-cli "saveworld" >/dev/null; }
    flush_on()  { :; }
    ;;
  *) echo "✗ Unknown game '$game'" >&2; exit 1 ;;
esac

archive="$dest_dir/${game}-${stamp}.tar.gz"

echo "→ [$game] flushing world to disk via RCON…"
if docker ps --format '{{.Names}}' | grep -qx "$container"; then
  flush_off || echo "  (warning: RCON flush failed — server may be offline; continuing)"
  trap 'flush_on || true' EXIT          # always re-enable autosave, even on error
  sleep 3
else
  echo "  (server not running — taking a cold snapshot)"
fi

[[ -d "$data" ]] || { echo "✗ Data dir not found: $data" >&2; exit 1; }

echo "→ [$game] creating $archive"
tar -czf "$archive" -C "$(dirname "$data")" "$(basename "$data")"
size="$(du -h "$archive" | cut -f1)"
echo "✓ [$game] snapshot ${size}"

# ── Local rotation ───────────────────────────────────────────────────────────
# shellcheck disable=SC2012  # snapshot names are controlled timestamps, no special chars
mapfile -t old < <(ls -1t "$dest_dir"/*.tar.gz 2>/dev/null | tail -n +"$((BACKUP_KEEP_LOCAL + 1))")
if ((${#old[@]})); then
  printf '%s\n' "${old[@]}" | xargs -r rm -f
  echo "✓ [$game] pruned ${#old[@]} old local snapshot(s), keeping $BACKUP_KEEP_LOCAL"
fi

# ── Offsite push (optional) ──────────────────────────────────────────────────
if [[ -n "$RCLONE_REMOTE" ]] && command -v rclone >/dev/null; then
  echo "→ [$game] pushing offsite to $RCLONE_REMOTE/$game/"
  rclone copy "$archive" "$RCLONE_REMOTE/$game/" --no-traverse
  # prune remote to last N
  rclone lsf "$RCLONE_REMOTE/$game/" --files-only 2>/dev/null \
    | sort -r | tail -n +"$((BACKUP_KEEP_REMOTE + 1))" \
    | while read -r f; do rclone deletefile "$RCLONE_REMOTE/$game/$f"; done
  echo "✓ [$game] offsite copy complete"
elif [[ -n "$RCLONE_REMOTE" ]]; then
  echo "  (RCLONE_REMOTE set but rclone not installed — see scripts/host-setup.sh)"
else
  echo "  (no RCLONE_REMOTE configured — local backup only)"
fi

echo "✓ [$game] backup done: $archive"
