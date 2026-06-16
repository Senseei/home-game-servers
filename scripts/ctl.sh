#!/usr/bin/env bash
# ctl.sh — unified control for all game servers.
#
#   ./scripts/ctl.sh up      minecraft
#   ./scripts/ctl.sh down    minecraft
#   ./scripts/ctl.sh logs    palworld
#   ./scripts/ctl.sh status
#   ./scripts/ctl.sh console minecraft   # attach to RCON console
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GAMES_DIR="$ROOT/games"
VALID_GAMES=(minecraft palworld ark-se)

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

cmd="${1:-help}"; game="${2:-}"
case "$cmd" in
  up)      require_game "$game"; compose "$game" up -d; echo "✓ $game starting — ./scripts/ctl.sh logs $game" ;;
  down)    require_game "$game"; compose "$game" down;  echo "✓ $game stopped" ;;
  restart) require_game "$game"; compose "$game" restart ;;
  logs)    require_game "$game"; compose "$game" logs -f --tail=100 ;;
  console)
    require_game "$game"
    case "$game" in
      minecraft) docker exec -it mc rcon-cli ;;
      palworld)  docker exec -it palworld-server rcon-cli ;;
      ark-se)    docker exec -it ark-server rcon-cli ;;
    esac ;;
  status)
    printf "%-12s %s\n" "GAME" "STATE"
    for g in "${VALID_GAMES[@]}"; do
      state=$(compose "$g" ps --format '{{.State}}' 2>/dev/null | head -1)
      printf "%-12s %s\n" "$g" "${state:-stopped}"
    done ;;
  *)
    cat <<EOF
home-game-servers control

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
