# ARK: Survival Evolved — server settings

ARK config spans **three layers** in this image. The trap is that some settings
are applied on the launch line **every boot** and will *shadow* the same keys in
`GameUserSettings.ini` — so you must edit each setting in the *right* place or it
silently won't take.

| Layer | Edit it in | Use it for |
|-------|-----------|------------|
| **Env vars** (compose / `.env`) | `games/ark-se/compose.yaml` + `.env` | server name, map, passwords, max players, mods — see table below |
| **arkmanager `main.cfg`** | `games/ark-se/server/arkmanager/instances/main.cfg` | **events**, PvE/PvP, spectator password, any custom `?`/`-` launch flag |
| **`GameUserSettings.ini` / `Game.ini`** | under `…/Saved/Config/LinuxServer/` | **difficulty, rates, player/dino stats** (anything not on the launch line) |

> All three files live under the image's native volume (`./server` → `/app`).
> They're generated on the **first boot** — boot once, then stop, then edit.
> **Do not set `ARK_SERVER_VOLUME`**: the image bakes `ARK_TOOLS_DIR=/app/arkmanager`
> at build time, so overriding the volume splits binaries from config → crash loop.

## Config file locations (host paths)

```
games/ark-se/server/arkmanager/instances/main.cfg                                  ← events, PvE, spectator, custom flags
games/ark-se/server/server/ShooterGame/Saved/Config/LinuxServer/GameUserSettings.ini  ← difficulty, rates
games/ark-se/server/server/ShooterGame/Saved/Config/LinuxServer/Game.ini              ← player/dino stat multipliers
```

(The `server/server/…` doubling is correct: `./server` → `/app`, then the image's own `server/` subdir.)

## Apply workflow

1. First boot to generate the files: `make up GAME=ark-se` → let it finish installing → `make down GAME=ark-se`.
2. Edit the file(s) for the settings you want (see below).
3. `make up GAME=ark-se` to apply (90s stop-grace lets it `saveworld` cleanly).
4. On an **existing** world, changing difficulty/stats may need `DestroyWildDinos` via RCON (localhost :27020) so creatures respawn under the new values.

## 1. Core settings → env vars (compose / `.env`)

These are written onto the launch line every boot, so set them as env vars —
**editing them in `GameUserSettings.ini` will not stick.**

| Setting | Env var | Default |
|---------|---------|---------|
| Server / session name | `ARK_SESSION_NAME` | — |
| Map | `ARK_MAP` | `TheIsland` |
| Join password | `SERVER_PASSWORD` | (blank = open) |
| Admin password (also RCON login) | `ADMIN_PASSWORD` | — |
| Max players | `ARK_MAX_PLAYERS` | `20` |
| Steam Workshop mods | `ARK_MODS` (→ `GAME_MOD_IDS`) | — |

## 2. Events & custom launch flags → `main.cfg`

There is **no env var** for arbitrary launch options. Edit
`games/ark-se/server/arkmanager/instances/main.cfg` and use arkmanager's three
prefixes (it builds the launch line from these):

| Prefix in `main.cfg` | Becomes on launch | For |
|----------------------|-------------------|-----|
| `ark_<Name>=<val>`   | `?Name=val`       | `?`-style query options |
| `arkopt_<Name>=<val>`| `-Name=<val>`     | `-`options **with** a value |
| `arkflag_<Name>=true`| `-Name`           | `-`flags (no value) |

```ini
# Seasonal event (a -option with a value)
arkopt_ActiveEvent=WinterWonderland     # or Easter, Summer, FearEvolved, birthday, …

# PvE mode + no spectator-on-death (?-style + flag)
ark_ServerPVE=true
arkflag_DisableDeathSpectator=true

# Spectator password (no env var for this)
ark_SpectatorPassword=change-me-spectator
```

Restart after editing. These persist — the image only seeds `main.cfg` from a
template if it's **absent**, and never rewrites it afterward.

## 3. Difficulty → `GameUserSettings.ini`

In `[ServerSettings]` (not shadowed by the launch line, so the ini is correct here):

```ini
[ServerSettings]
DifficultyOffset=1.0
OverrideOfficialDifficulty=5.0   ; 5.0 × 30 = level-150 wild dinos, like official
```

## 4. Player & dino stats → `Game.ini`

This file is **never touched** by the image — hand edits always persist. Stats are
`PerLevelStatsMultiplier_*` arrays under `[/script/shootergame.shootergamemode]`,
indexed by stat:

| idx | stat | idx | stat |
|----|------|----|------|
| 0 | Health | 7 | Weight |
| 1 | Stamina | 8 | Melee Damage |
| 2 | Torpidity | 9 | Movement Speed |
| 3 | Oxygen | 10 | Fortitude |
| 4 | Food | 11 | Crafting Speed |
| 5 | Water | | |

Four arrays tune each population separately:

```ini
[/script/shootergame.shootergamemode]
PerLevelStatsMultiplier_Player[7]=2.0            ; players: 2× weight per level
PerLevelStatsMultiplier_Player[8]=1.5            ; players: 1.5× melee per level
PerLevelStatsMultiplier_DinoWild[0]=1.5          ; wild dinos: 1.5× health
PerLevelStatsMultiplier_DinoTamed[0]=1.0         ; tamed: base
PerLevelStatsMultiplier_DinoTamed_Add[0]=0.5     ; tamed: per post-tame level-up
PerLevelStatsMultiplier_DinoTamed_Affinity[0]=1.0 ; tamed: perfect-tame bonus
```

(`_Player` = you · `_DinoWild` = wild spawns · `_DinoTamed` = tamed base ·
`_DinoTamed_Add` = levels added after taming.)

## 5. Rates → `GameUserSettings.ini` (`[ServerSettings]`)

```ini
[ServerSettings]
XPMultiplier=2.0
HarvestAmountMultiplier=2.0
TamingSpeedMultiplier=3.0
MatingIntervalMultiplier=0.5
EggHatchSpeedMultiplier=5.0
BabyMatureSpeedMultiplier=5.0
ResourcesRespawnPeriodMultiplier=0.5

; Do NOT set ServerPassword / ServerAdminPassword / MaxPlayers / SessionName
; here — those come from env vars (§1) and are overwritten on the launch line.
```

## RCON / admin

RCON is **on by default** (the arkmanager template ships `RCONEnabled=True`,
port from `RCON_PORT`, default `27020`). The compose binds it to `127.0.0.1`
only. It just needs a real `ADMIN_PASSWORD` — that's the RCON login and what the
backup save-flush script and `DestroyWildDinos` use.

Difficulty key semantics: <https://ark.wiki.gg/wiki/Difficulty>

## Known quirks / troubleshooting

Hard-won notes from first standing this up:

- **First boot is slow** — TheIsland generates the world on first run (several
  minutes). It's CPU-bound and **single-threaded**, so single-core clock matters.
  If it crawls, check the host isn't pinned to a low clock:
  `cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor` — `powersave` can
  hold cores at minimum (we saw 0.9 GHz). `sudo cpupower frequency-set -g performance`
  (and `echo 0 | sudo tee /sys/devices/system/cpu/intel_pstate/no_turbo`) fixes it.
- **`nofile` ulimit** — set to `100000` in the compose. ARK's Linux server can
  **hang at `Setting breakpad minidump`** if this is too low (Docker default 1024).
- **Checking if it's actually up** — the game port is **UDP**, and `docker exec
  ark-server ss -uln` as the unprivileged `steam` user **silently hides UDP sockets**
  (false "no port"). Check from a sidecar instead:
  `docker run --rm --net container:ark-server alpine sh -c 'apk add -q iproute2; ss -uln'`
  — you want `7777`, `7778`, and `27015` bound, plus `27020` (RCON) in `LISTEN`.
- **`Invalid steamcmd_user in config file`** — cosmetic arkmanager warning; it
  breaks `arkmanager status`/`rconcmd`, but the server's RCON port works fine.
- **`[S_API FAIL] SteamAPI_Init() failed`** — benign; every steamcmd ARK server
  prints it (no Steam *client* running). Not an error.
- **Host networking is ON (`network_mode: host` in the compose)** — Docker's bridge
  userland UDP proxy mangles game-server responses for *remote* clients (works
  locally, fails from other machines; hermsi issue #89), so ARK binds directly on
  the host NIC instead. Consequences: UFW now governs these ports
  (`sudo ufw allow 7777/udp 7778/udp 27015/udp`), and RCON `27020` is no longer
  auto-localhost — UFW's default-deny keeps it private (never forward it).
- **Joining your OWN server from the same LAN** fails via the public domain or the
  Steam-client browser — that's **NAT loopback**, which most routers won't hairpin.
  Connect by **LAN IP** instead: in-game *Join ARK → Session Filter → LAN* (or
  *Favorites* after adding `LANIP:27015` in Steam). Friends from outside use the
  domain normally. (Console `open LANIP:7777` also works, but not with a password.)

## Per-map config & mods

The live `Game.ini` / `GameUserSettings.ini` are gitignored (under `server/`); the
version-controlled config lives here, with a **shared default that maps inherit**:

```
games/ark-se/
  default/                 # the base config EVERY map inherits
    GameUserSettings.ini · Game.ini · mods
  maps/<Map>/              # only exists once you CUSTOMIZE a map (a copy of default to edit)
  mods.tsv                 # mod registry: ID <TAB> name (auto-filled from Steam)
```

A map **inherits `default/`** until you *customize* it. Customizing copies `default/`
into `maps/<Map>/` to edit; *uncustomizing* deletes that copy and the map inherits default
again. Saves are per-map natively and are never touched by any of this.

Starting a map runs `scripts/ark.sh apply <Map>`: sets `ARK_MAP`/`ARK_MODS` and loads
that map's config (its custom copy if any, else `default/`) — **merging** `[ServerSettings]`
into the live file (keeping image-managed keys like passwords) and replacing `Game.ini`.

**Everything's in the menu** (`./ctl` → `ark-se`):
- **up** → pick any map (official or customized) → loads its config+mods → starts. An
  untouched map just inherits `default/`.
- **customize** / **uncustomize** → give a map its own config (copy of default) / delete it.
- **edit-config** → pick `default` or a customized map → opens its `.ini` in `$EDITOR`
  (auto-customizes the map first).
- **edit-mods** → pick `default` or a map → toggle mods by name (gum); auto-customizes.
- **add-mod** → paste a Workshop ID → name auto-fetched into the registry.

[`scripts/ark.sh`](../../scripts/ark.sh) (`selectable | maps | apply | customize |
uncustomize | edit-config | edit-mods | mods-add | mods-sync`) backs all of these.
Current `default/` ruleset: lvl-300 wilds, PvE, 2× XP, 10× taming, 3× harvest, ½ dino
density, regen ×3, fast breeding (easy 100% imprints), flyer carry + speed leveling,
cave + no-collision building, 14 mods.
