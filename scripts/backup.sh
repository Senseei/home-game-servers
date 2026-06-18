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
# Load .env safely — values can contain spaces/quotes/apostrophes (e.g.
# ARK_SESSION_NAME=Gabriel's ARK), so parse KEY=VALUE rather than `source` it.
if [[ -f "$ROOT/.env" ]]; then
  while IFS='=' read -r _k _v; do
    [[ "$_k" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] && export "$_k=$_v"
  done < "$ROOT/.env"
fi

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
    container="ark-server"
    # Back up only the saves + config (~140M), NOT the 22GB re-downloadable install.
    data="$ROOT/games/ark-se/server/server/ShooterGame/Saved"
    # hermsi image has no rcon-cli; talk to ARK's RCON directly (host net, :27020).
    flush_off() { python3 "$ROOT/scripts/rcon.py" 127.0.0.1 "${ARK_RCON_PORT:-27020}" "$ADMIN_PASSWORD" saveworld >/dev/null; }
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
tar -czhf "$archive" -C "$(dirname "$data")" "$(basename "$data")"   # -h: follow the data symlink to the HDD
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
