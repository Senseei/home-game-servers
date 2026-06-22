# home-game-servers — ergonomic wrappers around ./ctl (the Java launcher)
# Usage:  make           (interactive menu)
#         make up GAME=minecraft   /   make backup GAME=palworld
GAME ?= minecraft
.DEFAULT_GOAL := menu

.PHONY: menu up down restart logs status backup restore setup
menu:     ; @./ctl
up:       ; @./ctl up $(GAME)
down:     ; @./ctl down $(GAME)
restart:  ; @./ctl restart $(GAME)
logs:     ; @./ctl logs $(GAME)
status:   ; @./ctl status
backup:   ; @./ctl backup $(GAME)
restore:  ; @./ctl restore $(GAME) $(FILE)
setup:    ; @./scripts/host-setup.sh
