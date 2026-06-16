# home-game-servers

Self-hosted, config-as-code game servers for a spare Linux laptop.
The repo **is** the server: every game is a Docker Compose definition, with a
single control script, a save-flushing backup pipeline (local + offsite), and
CI that validates everything on push.

Supported today: **Minecraft (Paper)**, **Palworld**, **ARK: Survival Evolved** —
all with mod support.

```
games/<game>/compose.yaml   one Compose file per game (the server definition)
scripts/ctl.sh              up / down / logs / console / status
scripts/backup.sh           RCON save-flush → tarball → rotate → offsite
scripts/restore.sh          restore a snapshot (local or offsite)
scripts/host-setup.sh       one-time laptop setup (Docker, Tailscale, rclone…)
systemd/                    timer templates for hands-off backups
.github/workflows/ci.yaml   lint + compose validation
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
./scripts/host-setup.sh          # installs Docker, Tailscale, rclone; disables lid-sleep
cp .env.example .env             # then edit .env — set passwords, RAM, mods
```

## 2. Let your friends connect (read this — it's the part that bites people)

Most Brazilian home connections are behind **CGNAT**, so you have no public IPv4
and port-forwarding won't work. Check: if your router's WAN IP ≠ what
[whatismyip.com](https://whatismyip.com) shows, you're behind CGNAT.

**Recommended fix — Tailscale** (already installed by `host-setup.sh`):

```bash
sudo tailscale up
```

Have each friend install Tailscale and join your tailnet. They then connect to
the server using your laptop's Tailscale IP (`100.x.y.z`) instead of a public
one — e.g. Minecraft → `100.x.y.z:25565`. This works through CGNAT, exposes
nothing to the public internet, and supports UDP (Palworld/ARK). You connect
locally via `localhost`.

> Prefer a real public IP? Either ask your ISP to take you off CGNAT, or run a
> tiny cloud VPS as a WireGuard relay pointing back here. Tailscale is simpler.

## 3. Run a server

```bash
make up GAME=minecraft        # or: ./scripts/ctl.sh up minecraft
make logs GAME=minecraft
make status
make down GAME=minecraft
```

You typically run **one game at a time** (16 GB each for Palworld/ARK).

## 4. Mods

- **Minecraft** — set `MC_MODRINTH_PROJECTS` in `.env` (comma-separated Modrinth
  slugs) for auto-download, or drop `.jar` plugins into `games/minecraft/plugins/`.
- **ARK** — set `ARK_MODS` to comma-separated Steam Workshop IDs; the server
  downloads them on boot.
- **Palworld** — mostly config-driven via `games/palworld/data/.../PalWorldSettings.ini`.

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
- [ ] Using Tailscale, or only the game ports forwarded — never RCON
- [ ] `docker` updates pulled periodically (`docker compose pull`)

## CI

Every push runs shellcheck on the scripts and `docker compose config` on each
game. To later close the loop (push → laptop auto-redeploys), add a
[self-hosted runner](https://docs.github.com/en/actions/hosting-your-own-runners)
on the laptop and a deploy job that runs `git pull && ctl.sh up`.
