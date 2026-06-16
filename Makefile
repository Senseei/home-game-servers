# home-game-servers — ergonomic wrappers around scripts/ctl.sh
# Usage:  make up GAME=minecraft   /   make backup GAME=palworld
GAME ?= minecraft

.PHONY: up down restart logs console status backup restore setup
up:       ; @./scripts/ctl.sh up $(GAME)
down:     ; @./scripts/ctl.sh down $(GAME)
restart:  ; @./scripts/ctl.sh restart $(GAME)
logs:     ; @./scripts/ctl.sh logs $(GAME)
console:  ; @./scripts/ctl.sh console $(GAME)
status:   ; @./scripts/ctl.sh status
backup:   ; @./scripts/backup.sh $(GAME)
restore:  ; @./scripts/restore.sh $(GAME) $(FILE)
setup:    ; @./scripts/host-setup.sh
