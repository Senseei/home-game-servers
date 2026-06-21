#!/usr/bin/env bash
# ark.sh — ARK per-map config (inherits a shared default) + mod registry.
#
# A map INHERITS games/ark-se/default/ until you "customize" it — which copies
# default/ into maps/<Map>/ for you to edit. "uncustomize" deletes that copy and
# the map goes back to inheriting default. (World saves are per-map natively and
# are never touched by any of this.)
#
#   ark.sh selectable                  official maps + customized maps (menu picker)
#   ark.sh maps                        customized maps only
#   ark.sh apply       <Map>           load a map's config + mods into the live server
#   ark.sh customize   <Map>           give a map its own config (copies default/)
#   ark.sh uncustomize <Map>           delete a map's custom config (revert to default)
#   ark.sh edit-config <Map|default>   edit config in $EDITOR (auto-customizes a map)
#   ark.sh edit-mods   <Map|default>   gum mod picker (auto-customizes a map)
#   ark.sh mods-add    <id>            add a mod to the registry by Workshop ID
#   ark.sh mods-sync                   fetch missing mod names from Steam
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARK="$ROOT/games/ark-se"
DEFAULT="$ARK/default"
MAPS="$ARK/maps"
REGISTRY="$ARK/mods.tsv"
LIVE="$ARK/server/server/ShooterGame/Saved/Config/LinuxServer"

# Official ARK:SE maps (shipped in the install) — always selectable.
OFFICIAL=(TheIsland TheCenter ScorchedEarth_P Aberration_P Extinction Ragnarok Valguero_P CrystalIsles Genesis Gen2 LostIsland Fjordur)

die() { echo "✗ $*" >&2; exit 1; }
valid_name() { [[ "$1" =~ ^[A-Za-z0-9_]+$ ]] || die "bad map name '$1' (letters/digits/underscore only)"; }
is_custom() { [[ -d "$MAPS/$1" ]]; }                              # map has its own config?
src_dir()  { if is_custom "$1"; then echo "$MAPS/$1"; else echo "$DEFAULT"; fi; }   # where to read config/mods
mods_in()  { sed 's/#.*//' "$1" 2>/dev/null | tr -s ' \t' '\n' | grep -E '^[0-9]+$' || true; }
mod_name() { awk -F'\t' -v i="$1" '$1==i{print $2; f=1} END{if(!f) print i}' "$REGISTRY"; }

cmd_maps() { find "$MAPS" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null | sort; }

cmd_selectable() {
  local m all
  all="$( { printf '%s\n' "${OFFICIAL[@]}"; cmd_maps; } | sort -u )"
  while read -r m; do is_custom "$m" && echo "$m  ● custom" || echo "$m  ○ default"; done <<< "$all"
}

cmd_customize() {
  local m="${1:?usage: customize <Map>}"; valid_name "$m"
  is_custom "$m" && { echo "$m already has a custom config (edit-config $m)"; return 0; }
  mkdir -p "$MAPS/$m"
  cp "$DEFAULT/GameUserSettings.ini" "$DEFAULT/Game.ini" "$DEFAULT/mods" "$MAPS/$m/"
  echo "✓ $m now has its own config (copied from default) — edit-config $m / edit-mods $m"
}

cmd_uncustomize() {
  local m="${1:?usage: uncustomize <Map>}"; valid_name "$m"
  is_custom "$m" || { echo "$m already inherits default (no custom config)"; return 0; }
  rm -rf "${MAPS:?}/$m"
  echo "✓ $m reverted to default — world save untouched"
}

cmd_apply() {
  local m="${1:?usage: apply <Map>}"; valid_name "$m"
  local d; d="$(src_dir "$m")"
  local ids; ids="$(mods_in "$d/mods" | paste -sd, -)"
  sed -i "s/^ARK_MAP=.*/ARK_MAP=$m/" "$ROOT/.env"
  if grep -q '^ARK_MODS=' "$ROOT/.env"; then sed -i "s/^ARK_MODS=.*/ARK_MODS=$ids/" "$ROOT/.env"
  else echo "ARK_MODS=$ids" >> "$ROOT/.env"; fi
  mkdir -p "$LIVE"
  cp "$d/Game.ini" "$LIVE/Game.ini"
  if [[ -f "$LIVE/GameUserSettings.ini" ]]; then
    python3 "$ROOT/scripts/ark-merge-gus.py" "$LIVE/GameUserSettings.ini" "$d/GameUserSettings.ini" >/dev/null
  else
    cp "$d/GameUserSettings.ini" "$LIVE/GameUserSettings.ini"
  fi
  echo "✓ $m loaded — $(is_custom "$m" && echo 'custom config' || echo 'default config'), $(mods_in "$d/mods" | grep -c .) mods"
}

# Resolve an edit target: "default" → default/, else a map's custom dir (auto-customize).
target_dir() {
  local t="$1"
  [[ "$t" == default ]] && { echo "$DEFAULT"; return; }
  valid_name "$t"
  is_custom "$t" || cmd_customize "$t" >&2
  echo "$MAPS/$t"
}

cmd_edit_config() {
  local t="${1:?usage: edit-config <Map|default>}"; local d; d="$(target_dir "$t")"
  "${EDITOR:-nano}" "$d/GameUserSettings.ini" "$d/Game.ini"
  echo "→ saved $t config (applies on next 'up')"
}

cmd_mods_sync() {
  local all known missing
  all="$( { cut -f1 "$REGISTRY"; mods_in "$DEFAULT/mods"; for m in $(cmd_maps); do mods_in "$MAPS/$m/mods"; done; } | grep -E '^[0-9]+$' | sort -u )"
  known="$(grep -E '^[0-9]' "$REGISTRY" | cut -f1 | sort -u)"
  missing="$(comm -23 <(echo "$all") <(echo "$known") || true)"
  [[ -z "$missing" ]] && { echo "registry up to date ($(grep -cE '^[0-9]' "$REGISTRY") mods)"; return 0; }
  echo "→ fetching names: $(echo "$missing" | tr '\n' ' ')"
  # shellcheck disable=SC2086
  python3 "$ROOT/scripts/ark-mods-fetch.py" $missing >> "$REGISTRY"
  echo "✓ registry now has $(grep -cE '^[0-9]' "$REGISTRY") mods"
}

cmd_mods_add() {
  local id="${1:?usage: mods-add <id>}"
  [[ "$id" =~ ^[0-9]+$ ]] || die "mod id must be numeric (got '$id')"
  awk -F'\t' -v i="$id" '$1==i{f=1} END{exit !f}' "$REGISTRY" && { echo "already in registry: $id  $(mod_name "$id")"; return 0; }
  local line; line="$(python3 "$ROOT/scripts/ark-mods-fetch.py" "$id" 2>/dev/null)"
  [[ -n "$line" ]] || die "couldn't fetch mod $id from Steam — check the ID"
  printf '%s\n' "$line" >> "$REGISTRY"
  echo "✓ added to registry: $line"
}

cmd_mods_edit() {
  local t="${1:?usage: mods-edit <Map|default>}"
  command -v gum >/dev/null 2>&1 || die "gum not installed — needed for the mod editor"
  cmd_mods_sync >/dev/null || true
  local d; d="$(target_dir "$t")"
  local sel=() id name
  while read -r id; do name="$(mod_name "$id")"; [[ -n "$name" ]] && sel+=(--selected="$name"); done < <(mods_in "$d/mods")
  local picked
  picked="$(awk -F'\t' '/^[0-9]/{print $2}' "$REGISTRY" \
      | gum choose --no-limit --height=20 --header="Mods for $t  —  space toggles, enter saves" "${sel[@]}")" || return 0
  [[ -z "$picked" ]] && { echo "(no change)"; return 0; }
  { echo "# $t mods (edit via ./ctl: ark-se -> edit-mods)"
    while IFS= read -r name; do awk -F'\t' -v n="$name" '$2==n{print $1}' "$REGISTRY"; done <<< "$picked"
  } > "$d/mods"
  echo "✓ saved $(grep -cE '^[0-9]' "$d/mods") mods for $t"
}

case "${1:-}" in
  selectable)  cmd_selectable ;;
  maps)        cmd_maps ;;
  apply)       shift; cmd_apply "$@" ;;
  customize)   shift; cmd_customize "$@" ;;
  uncustomize) shift; cmd_uncustomize "$@" ;;
  edit-config) shift; cmd_edit_config "$@" ;;
  edit-mods)   shift; cmd_mods_edit "$@" ;;
  mods-add)    shift; cmd_mods_add "$@" ;;
  mods-sync)   cmd_mods_sync ;;
  *) echo "usage: ark.sh {selectable | maps | apply <Map> | customize <Map> | uncustomize <Map> | edit-config <Map|default> | edit-mods <Map|default> | mods-add <id> | mods-sync}" ;;
esac
