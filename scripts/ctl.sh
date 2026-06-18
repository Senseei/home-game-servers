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
# Official ARK maps — all shipped inside the server install (no extra download).
ARK_MAPS=(TheIsland Ragnarok Fjordur CrystalIsles TheCenter Valguero_P ScorchedEarth_P Aberration_P Extinction Genesis Gen2 LostIsland)

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

menu() {
  echo "home-game-servers — pick a server"
  local g labels=()
  for g in "${VALID_GAMES[@]}"; do
    if is_running "$g"; then labels+=("$g  ● running"); else labels+=("$g  ○ stopped"); fi
  done
  local choice; choice=$(pick "server>" "${labels[@]}" "quit") || return 0
  local game="${choice%% *}"
  [[ "$game" == quit ]] && return 0

  local action; action=$(pick "action>" up down restart logs console backup status) || return 0

  if [[ "$game" == ark-se && "$action" == up ]]; then
    local map; map=$(pick "ARK map (current: $(env_get ARK_MAP))>" "${ARK_MAPS[@]}") || return 0
    sed -i "s/^ARK_MAP=.*/ARK_MAP=$map/" "$ROOT/.env"
    echo "→ ARK map set to $map"
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
