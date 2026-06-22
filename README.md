# home-game-servers

Self-hosted, config-as-code game servers for a spare Linux laptop.
The repo **is** the server: every game is a Docker Compose definition, with a
single control script, a save-flushing backup pipeline (local + offsite), and
CI that validates everything on push.

Supported today: **Minecraft (Paper)**, **Palworld**, **ARK: Survival Evolved** —
all with mod support.

```
games/<game>/compose.yaml   one Compose file per game (the server definition)
ctl + launcher/             the control tool (Java): up/down/logs/status, backups,
                            ARK map+mod config — interactive menu or one-shot commands
scripts/host-setup.sh       one-time laptop setup (Docker, rclone, lid-sleep…)
systemd/                    timer templates for hands-off backups
.github/workflows/ci.yaml   Java build/test + shellcheck + compose validation
```

## Why this design

- **Reproducible** — the world data lives on the host (gitignored); the repo
  holds only *how to build the server*. Wipe the laptop, `git clone`, restore a
  backup, and you're back.
- **Isolated & safe** — each server runs in a container as a non-root user.
  RCON ports bind to `127.0.0.1` only.
- **Laptop-friendly** — Minecraft and Palworld auto-pause when empty so the
  machine isn't burning CPU between sessions.

## 1. First-time setup

```bash
git clone <your-repo> && cd home-game-servers
./scripts/host-setup.sh          # installs Docker, rclone; disables lid-sleep
cp .env.example .env             # then edit .env — set passwords, RAM, mods
```

## 2. Let your friends connect (read this — it's the part that bites people)

This host has a **real public IP**, so friends connect directly — no VPN. The
model is: reserve the laptop's LAN IP → port-forward the game ports on your
router → open the same ports on UFW → give friends a stable name via your
router's **dynamic DNS** client (optionally fronted by your own domain). RCON
stays bound to `127.0.0.1` and is never forwarded.

Full walkthrough — DHCP reservation, per-game ports, UFW commands, dynamic DNS,
and the custom-domain CNAME/SRV trick — is in **[NETWORKING.md](NETWORKING.md)**.
Because you're publicly exposed, use strong passwords and `docker compose pull`
regularly.

> Behind CGNAT with no public IP? Port-forwarding can't work there — if you ever
> need CGNAT traversal, see Tailscale.

## 3. Run a server

```bash
./ctl                         # ⭐ interactive arrow-key menu — pick game + action
# …or drive it directly:
./ctl up minecraft            # one-shot commands (or: make up GAME=minecraft)
./ctl logs minecraft
./ctl status
./ctl down minecraft
```

`./ctl` (no args, or bare `make`) opens an arrow-key menu: **Servers** (start/stop/
restart with live status), **Games → ARK** (switch map, edit config + mods, registry),
and **Backups**. With arguments it's a scriptable CLI. (First run builds the Java jar.)

You typically run **one game at a time** (16 GB each for Palworld/ARK).

## 4. Mods

- **Minecraft** — runs a **CurseForge modpack** (`CF_API_KEY` + `MC_CF_MODPACK` in
  `.env`); every player installs the same pack. See
  [`games/minecraft/README.md`](games/minecraft/README.md).
- **ARK** — set `ARK_MODS` to comma-separated Steam Workshop IDs; the server
  downloads them on boot. Server settings/difficulty:
  [`games/ark-se/README.md`](games/ark-se/README.md).
- **Palworld** — config-driven; importing an existing world:
  [`games/palworld/README.md`](games/palworld/README.md).

## 5. Backups

Manual:

```bash
make backup GAME=minecraft
make restore GAME=minecraft FILE=latest
```

Hands-off: install the systemd timers (see [`systemd/README.md`](systemd/README.md))
to back up every game every 2 hours.

**Offsite is essential** — a laptop is a single point of failure. Configure an
rclone remote (`rclone config`) to **Backblaze B2** (10 GB free) or
**Cloudflare R2** (10 GB free, no egress fees), then set `RCLONE_REMOTE` in
`.env`. Backups then rotate locally *and* offsite automatically.

## Security checklist

- [ ] `.env` is gitignored (it is) and never committed
- [ ] RCON ports stay bound to `127.0.0.1` (they are, in the compose files)
- [ ] Only the game ports forwarded + opened on UFW — never RCON
- [ ] `docker` updates pulled periodically (`docker compose pull`)

## CI

Every push builds + tests the Java launcher (`mvn package`), runs shellcheck on the
shell bits, and `docker compose config` on each game. To later close the loop (push →
laptop auto-redeploys), add a
[self-hosted runner](https://docs.github.com/en/actions/hosting-your-own-runners)
on the laptop and a deploy job that runs `git pull && ./ctl up`.
