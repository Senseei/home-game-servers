#!/usr/bin/env bash
# ark.sh — ARK per-map config + mod registry.
#
#   ./scripts/ark.sh maps                  list configured maps (games/ark-se/maps/*)
#   ./scripts/ark.sh apply   <Map>         load a map's config + mods into the live server (sets ARK_MAP/ARK_MODS, merges ini)
#   ./scripts/ark.sh map-new <Map> [from]  scaffold a new map by copying another (default: TheCenter)
#   ./scripts/ark.sh mods-sync             fetch missing mod names from Steam into mods.tsv
#   ./scripts/ark.sh mods-edit <Map>       pick this map's mods by name (gum), write its list
#
# The menu (./ctl) calls `apply` before starting ARK, and `mods-edit` for the editor.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARK="$ROOT/games/ark-se"
MAPS="$ARK/maps"
REGISTRY="$ARK/mods.tsv"
LIVE="$ARK/server/server/ShooterGame/Saved/Config/LinuxServer"

die() { echo "✗ $*" >&2; exit 1; }

# Mod IDs (one per line) from a map's mods file — strips comments, blanks, inline notes.
map_mod_ids() { sed 's/#.*//' "$MAPS/$1/mods" 2>/dev/null | tr -s ' \t' '\n' | grep -E '^[0-9]+$' || true; }

# Look up a name in the registry by id (or echo the id if unknown).
mod_name() { awk -F'\t' -v i="$1" '$1==i{print $2; found=1} END{if(!found)print i}' "$REGISTRY"; }

cmd_maps() { find "$MAPS" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null | sort; }

cmd_apply() {
  local m="${1:?usage: apply <Map>}"; local d="$MAPS/$m"
  [[ -d "$d" ]] || die "no such map '$m' — see: ark.sh maps"
  local ids; ids="$(map_mod_ids "$m" | paste -sd, -)"

  # ARK_MAP + ARK_MODS into .env (drives the image's map + mod install/load)
  sed -i "s/^ARK_MAP=.*/ARK_MAP=$m/" "$ROOT/.env"
  if grep -q '^ARK_MODS=' "$ROOT/.env"; then sed -i "s/^ARK_MODS=.*/ARK_MODS=$ids/" "$ROOT/.env"
  else echo "ARK_MODS=$ids" >> "$ROOT/.env"; fi

  # Game.ini: full replace (the image never writes it).
  [[ -f "$d/Game.ini" ]] && { mkdir -p "$LIVE"; cp "$d/Game.ini" "$LIVE/Game.ini"; }
  # GameUserSettings.ini: merge the map's [ServerSettings] (preserve image-managed keys); copy if no live file yet.
  if [[ -f "$d/GameUserSettings.ini" ]]; then
    if [[ -f "$LIVE/GameUserSettings.ini" ]]; then
      python3 "$ROOT/scripts/ark-merge-gus.py" "$LIVE/GameUserSettings.ini" "$d/GameUserSettings.ini" >/dev/null
    else
      mkdir -p "$LIVE"; cp "$d/GameUserSettings.ini" "$LIVE/GameUserSettings.ini"
    fi
  fi
  echo "✓ map '$m' loaded — ARK_MAP=$m, $(map_mod_ids "$m" | grep -c .) mods, config merged into live"
}

cmd_map_new() {
  local m="${1:?usage: map-new <Map> [from]}"; local from="${2:-TheCenter}"
  [[ -d "$MAPS/$m" ]] && die "map '$m' already exists"
  [[ -d "$MAPS/$from" ]] || die "template map '$from' not found"
  cp -r "$MAPS/$from" "$MAPS/$m"
  echo "✓ scaffolded maps/$m (from $from) — tweak its .ini files, then: ark.sh mods-edit $m"
}

cmd_mods_sync() {
  local all known missing
  all="$( { cut -f1 "$REGISTRY"; for m in $(cmd_maps); do map_mod_ids "$m"; done; } | grep -E '^[0-9]+$' | sort -u )"
  known="$(grep -E '^[0-9]' "$REGISTRY" | cut -f1 | sort -u)"
  missing="$(comm -23 <(echo "$all") <(echo "$known") || true)"
  [[ -z "$missing" ]] && { echo "registry up to date ($(grep -cE '^[0-9]' "$REGISTRY") mods)"; return 0; }
  echo "→ fetching names: $(echo "$missing" | tr '\n' ' ')"
  # shellcheck disable=SC2086
  python3 "$ROOT/scripts/ark-mods-fetch.py" $missing >> "$REGISTRY"
  echo "✓ registry now has $(grep -cE '^[0-9]' "$REGISTRY") mods"
}

cmd_mods_edit() {
  local m="${1:?usage: mods-edit <Map>}"; [[ -d "$MAPS/$m" ]] || die "no such map '$m'"
  command -v gum >/dev/null 2>&1 || die "gum not installed — needed for the mod editor"
  cmd_mods_sync >/dev/null || true            # make sure the catalog has names for everything

  # Pre-select the map's current mods (by name); offer the whole registry to toggle.
  local sel=() id name
  while read -r id; do
    name="$(mod_name "$id")"; [[ -n "$name" ]] && sel+=(--selected="$name")
  done < <(map_mod_ids "$m")

  local picked
  picked="$(awk -F'\t' '/^[0-9]/{print $2}' "$REGISTRY" \
      | gum choose --no-limit --height=20 --header="Mods for $m  —  space toggles, enter saves" "${sel[@]}")" || return 0
  [[ -z "$picked" ]] && { echo "(no change)"; return 0; }

  { echo "# $m — Workshop mod IDs this map loads (edit: ./scripts/ark.sh mods-edit $m)"
    while IFS= read -r name; do awk -F'\t' -v n="$name" '$2==n{print $1}' "$REGISTRY"; done <<< "$picked"
  } > "$MAPS/$m/mods"
  echo "✓ saved $(grep -cE '^[0-9]' "$MAPS/$m/mods") mods to maps/$m/mods"
}

case "${1:-}" in
  maps)      cmd_maps ;;
  apply)     shift; cmd_apply "$@" ;;
  map-new)   shift; cmd_map_new "$@" ;;
  mods-sync) cmd_mods_sync ;;
  mods-edit) shift; cmd_mods_edit "$@" ;;
  *) echo "usage: ark.sh {maps | apply <Map> | map-new <Map> [from] | mods-sync | mods-edit <Map>}" ;;
esac
