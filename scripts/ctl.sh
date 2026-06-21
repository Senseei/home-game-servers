#!/usr/bin/env bash
# ctl.sh — unified control for all game servers.
#
#   ./scripts/ctl.sh                 # interactive menu (also: ./ctl  or  make menu)
#   ./scripts/ctl.sh up      ark-se  # direct command (used by scripts / systemd timers)
#   ./scripts/ctl.sh down    minecraft
#   ./scripts/ctl.sh logs    palworld
#   ./scripts/ctl.sh console ark-se  # RCON console
#   ./scripts/ctl.sh status
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GAMES_DIR="$ROOT/games"
VALID_GAMES=(minecraft palworld ark-se)
# ARK per-map config + mods live in games/ark-se/maps/, driven by scripts/ark.sh.

die() { echo "✗ $*" >&2; exit 1; }

require_game() {
  local g="${1:-}"
  [[ -n "$g" ]] || die "Specify a game: ${VALID_GAMES[*]}"
  [[ " ${VALID_GAMES[*]} " == *" $g "* ]] || die "Unknown game '$g'. Valid: ${VALID_GAMES[*]}"
  [[ -f "$GAMES_DIR/$g/compose.yaml" ]] || die "Missing $GAMES_DIR/$g/compose.yaml"
}

compose() {            # compose <game> <args...>  — loads root .env automatically
  local g="$1"; shift
  ( cd "$GAMES_DIR/$g" && docker compose --env-file "$ROOT/.env" "$@" )
}

container_of() { case "$1" in minecraft) echo mc ;; palworld) echo palworld-server ;; ark-se) echo ark-server ;; esac; }
is_running()   { docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$(container_of "$1")"; }
env_get()      { grep -E "^$1=" "$ROOT/.env" 2>/dev/null | head -1 | cut -d= -f2- || true; }

# pick "<prompt>" choice...  — fzf if available (arrows + type-to-filter), else a numbered menu.
pick() {
  local prompt="$1"; shift
  if command -v fzf >/dev/null 2>&1; then
    printf '%s\n' "$@" | fzf --prompt="$prompt " --height=40% --reverse --no-info --no-multi
  else
    local opt
    PS3="$prompt "
    select opt in "$@"; do [[ -n "$opt" ]] && { printf '%s\n' "$opt"; return 0; }; done
    return 1   # EOF / cancelled
  fi
}

# ask "<prompt>"  — one-line text input (gum if present, else read).
ask() {
  if command -v gum >/dev/null 2>&1; then gum input --prompt "$1 "
  else local a; read -rp "$1 " a; printf '%s\n' "$a"; fi
}

menu() {
  echo "home-game-servers — pick a server"
  local g labels=()
  for g in "${VALID_GAMES[@]}"; do
    if is_running "$g"; then labels+=("$g  ● running"); else labels+=("$g  ○ stopped"); fi
  done
  local choice; choice=$(pick "server>" "${labels[@]}" "quit") || return 0
  local game="${choice%% *}"
  [[ "$game" == quit ]] && return 0

  local actions=(up down restart logs console backup status)
  [[ "$game" == ark-se ]] && actions=(up edit-mods edit-config add-mod new-map del-map down restart logs console backup status)
  local action; action=$(pick "action>" "${actions[@]}") || return 0

  # ARK: edit a map's mod list (gum) — does not start the server
  if [[ "$game" == ark-se && "$action" == edit-mods ]]; then
    local emaps; mapfile -t emaps < <("$ROOT/scripts/ark.sh" maps)
    local emap; emap=$(pick "edit mods for map>" "${emaps[@]}") || return 0
    "$ROOT/scripts/ark.sh" mods-edit "$emap"
    return 0
  fi

  # ARK: edit a map's config (.ini) in $EDITOR
  if [[ "$game" == ark-se && "$action" == edit-config ]]; then
    local cmaps; mapfile -t cmaps < <("$ROOT/scripts/ark.sh" maps)
    local cmap; cmap=$(pick "edit config for map>" "${cmaps[@]}") || return 0
    "${EDITOR:-nano}" "$GAMES_DIR/ark-se/maps/$cmap/GameUserSettings.ini" "$GAMES_DIR/ark-se/maps/$cmap/Game.ini"
    echo "→ edits apply next time you 'up' $cmap"
    return 0
  fi

  # ARK: add a mod to the registry by Workshop ID (name auto-fetched)
  if [[ "$game" == ark-se && "$action" == add-mod ]]; then
    local mid; mid=$(ask "Steam Workshop mod ID>") || return 0
    [[ -n "$mid" ]] && "$ROOT/scripts/ark.sh" mods-add "$mid"
    return 0
  fi

  # ARK: scaffold a new map (copies TheCenter)
  if [[ "$game" == ark-se && "$action" == new-map ]]; then
    local nm; nm=$(ask "new map — ARK code (Ragnarok, Fjordur, Valguero_P…)>") || return 0
    [[ -n "$nm" ]] && "$ROOT/scripts/ark.sh" map-new "$nm"
    return 0
  fi

  # ARK: delete a map's config (the world save is kept)
  if [[ "$game" == ark-se && "$action" == del-map ]]; then
    local dmaps; mapfile -t dmaps < <("$ROOT/scripts/ark.sh" maps)
    local dmap; dmap=$(pick "delete config for map>" "${dmaps[@]}") || return 0
    if command -v gum >/dev/null 2>&1; then gum confirm "Delete maps/$dmap config? (world save is kept)" || return 0
    else local yn; read -rp "Delete maps/$dmap config? [y/N] " yn; [[ "$yn" == [yY] ]] || return 0; fi
    "$ROOT/scripts/ark.sh" map-del "$dmap"
    return 0
  fi

  # ARK: pick the map, load its per-map config + mods, then start
  if [[ "$game" == ark-se && "$action" == up ]]; then
    local amaps; mapfile -t amaps < <("$ROOT/scripts/ark.sh" maps)
    local map; map=$(pick "ARK map (current: $(env_get ARK_MAP))>" "${amaps[@]}") || return 0
    "$ROOT/scripts/ark.sh" apply "$map"
  fi

  case "$action" in
    backup) "$ROOT/scripts/backup.sh" "$game" ;;
    *)      "${BASH_SOURCE[0]}" "$action" "$game" ;;
  esac
}

cmd="${1:-menu}"; game="${2:-}"
case "$cmd" in
  menu)    menu ;;
  up)      require_game "$game"; compose "$game" up -d; echo "✓ $game starting — ./scripts/ctl.sh logs $game" ;;
  down)    require_game "$game"; compose "$game" down;  echo "✓ $game stopped" ;;
  restart) require_game "$game"; compose "$game" restart ;;
  logs)    require_game "$game"; compose "$game" logs -f --tail=100 ;;
  console)
    require_game "$game"
    case "$game" in
      minecraft) docker exec -it mc rcon-cli ;;
      palworld)  docker exec -it palworld-server rcon-cli ;;
      ark-se)    # hermsi image has no rcon-cli — small RCON prompt via scripts/rcon.py
        echo "ARK RCON — commands like: listplayers, saveworld, broadcast hi, DestroyWildDinos. 'exit' to quit."
        ark_pw="$(env_get ADMIN_PASSWORD)"
        while read -rp "rcon> " ark_line; do
          [[ "$ark_line" == exit ]] && break
          [[ -n "$ark_line" ]] && python3 "$ROOT/scripts/rcon.py" 127.0.0.1 27020 "$ark_pw" "$ark_line"
        done ;;
    esac ;;
  status)
    printf "%-12s %s\n" "GAME" "STATE"
    for g in "${VALID_GAMES[@]}"; do
      if is_running "$g"; then printf "%-12s %s\n" "$g" "running"; else printf "%-12s %s\n" "$g" "stopped"; fi
    done ;;
  *)
    cat <<EOF
home-game-servers control

  ./scripts/ctl.sh                  interactive menu   (also: ./ctl  or  make menu)
  ./scripts/ctl.sh up      <game>   start a server (detached)
  ./scripts/ctl.sh down    <game>   stop a server
  ./scripts/ctl.sh restart <game>   restart
  ./scripts/ctl.sh logs    <game>   follow logs
  ./scripts/ctl.sh console <game>   open RCON console
  ./scripts/ctl.sh status           show all servers

  games: ${VALID_GAMES[*]}
EOF
    ;;
esac
