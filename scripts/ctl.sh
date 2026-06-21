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
  [[ "$game" == ark-se ]] && actions=(up customize uncustomize edit-config edit-mods add-mod down restart logs console backup status)
  local action; action=$(pick "action>" "${actions[@]}") || return 0

  # ARK: customize a map — give it its own config (copies default/)
  if [[ "$game" == ark-se && "$action" == customize ]]; then
    local _cs; mapfile -t _cs < <("$ROOT/scripts/ark.sh" selectable)
    local cs; cs=$(pick "customize which map>" "${_cs[@]}") || return 0
    "$ROOT/scripts/ark.sh" customize "${cs%% *}"
    return 0
  fi

  # ARK: uncustomize — delete a map's custom config, revert to default
  if [[ "$game" == ark-se && "$action" == uncustomize ]]; then
    local _um; mapfile -t _um < <("$ROOT/scripts/ark.sh" maps)
    [[ ${#_um[@]} -eq 0 ]] && { echo "no customized maps yet"; return 0; }
    local um; um=$(pick "revert which map to default>" "${_um[@]}") || return 0
    if command -v gum >/dev/null 2>&1; then gum confirm "Delete $um custom config? (world save kept)" || return 0
    else local yn; read -rp "Delete $um custom config? [y/N] " yn; [[ "$yn" == [yY] ]] || return 0; fi
    "$ROOT/scripts/ark.sh" uncustomize "$um"
    return 0
  fi

  # ARK: edit config — default, or a customized map (auto-customizes) — in $EDITOR
  if [[ "$game" == ark-se && "$action" == edit-config ]]; then
    local _ec; mapfile -t _ec < <(printf 'default\n'; "$ROOT/scripts/ark.sh" maps)
    local ec; ec=$(pick "edit config for>" "${_ec[@]}") || return 0
    "$ROOT/scripts/ark.sh" edit-config "$ec"
    return 0
  fi

  # ARK: edit mods — default, or a customized map (auto-customizes) — gum checklist
  if [[ "$game" == ark-se && "$action" == edit-mods ]]; then
    local _em; mapfile -t _em < <(printf 'default\n'; "$ROOT/scripts/ark.sh" maps)
    local em; em=$(pick "edit mods for>" "${_em[@]}") || return 0
    "$ROOT/scripts/ark.sh" edit-mods "$em"
    return 0
  fi

  # ARK: add a mod to the registry by Workshop ID (name auto-fetched)
  if [[ "$game" == ark-se && "$action" == add-mod ]]; then
    local mid; mid=$(ask "Steam Workshop mod ID>") || return 0
    [[ -n "$mid" ]] && "$ROOT/scripts/ark.sh" mods-add "$mid"
    return 0
  fi

  # ARK: pick a map (official or customized), load its config+mods, then start
  if [[ "$game" == ark-se && "$action" == up ]]; then
    local _us; mapfile -t _us < <("$ROOT/scripts/ark.sh" selectable)
    local us; us=$(pick "ARK map (current: $(env_get ARK_MAP))>" "${_us[@]}") || return 0
    "$ROOT/scripts/ark.sh" apply "${us%% *}"
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
