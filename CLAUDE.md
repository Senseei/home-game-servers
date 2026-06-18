# CLAUDE.md

Context for `home-game-servers` — config-as-code game servers (Minecraft
Paper/CurseForge, Palworld, ARK: Survival Evolved) on a Linux host via Docker Compose.

## Layout & conventions

- `games/<game>/compose.yaml` — one server per game. Run **one game at a time** (RAM).
- `scripts/ctl.sh` — `up | down | restart | logs | console | status`. It loads the
  root `.env` (`docker compose --env-file "$ROOT/.env"`), so **use it** (or `make`),
  not raw `docker compose` from a game dir (which would look for `.env` in the wrong place).
- `.env` (gitignored) holds secrets + per-game knobs, **namespaced** (`MC_*`, `PAL_*`,
  `ARK_*`). The composes map them to each image's real env names via `${VAR}`
  interpolation (e.g. `MEMORY: "${MC_MEMORY}"`) — this is why you can't replace it with
  a bare `env_file:`.
- **Gitignored:** `.env`, `games/*/server`, `games/*/data` (installs + world saves; large).
  So a clone gives config only — games re-download via steamcmd on first boot.
- **RCON** binds to `127.0.0.1` only. Exception: ARK uses `network_mode: host` (Docker's
  bridge UDP proxy mangles game traffic for *remote* clients), so UFW governs ARK's ports
  and RCON `27020` stays private via UFW's default-deny. See `NETWORKING.md`.
- Per-game setup, settings, and quirks: `games/<game>/README.md`. Networking model
  (DHCP reservation → router forward → UFW → router DDNS + domain CNAME): `NETWORKING.md`.
- CI (`.github/workflows/ci.yaml`): shellcheck + `docker compose config` per game.

## Running on Windows via WSL2 (more disk / RAM than the spare laptop)

The "Linux host" can be a Windows PC running **WSL2 + Docker Desktop** (with WSL
integration enabled in Docker Desktop settings). It works, but get three things right.

### 1. Filesystem — clone into WSL2's ext4, NOT `/mnt/c`
Clone into the WSL2 home (`~/home-game-servers`). The installs are 20 GB+ with heavy
I/O; `/mnt/c/...` (the Windows drive via the 9p bridge) is far too slow for that.
WSL2's ext4 lives in a vhdx **on your Windows drive**, so you get the big drive's
capacity at native Linux speed — which is the whole reason to do this.

### 2. `.wslconfig` — give WSL2 enough RAM (it's capped by default)
WSL2 defaults to **~50% of Windows RAM (8 GB on older builds)** — too little if a game
caps at 12 GB (`ARK_MEMORY`/`PAL_MEMORY`). Create `%UserProfile%\.wslconfig` on the
**Windows** side:

```ini
[wsl2]
memory=12GB                # ≥ your game's *_MEMORY + headroom; leave some for Windows
processors=8
swap=4GB
networkingMode=mirrored    # Windows 11 22H2+ only — see networking below
```

Apply with `wsl --shutdown` (in Windows PowerShell), then reopen WSL. Verify inside
WSL with `free -h` / `nproc`.

### 3. Networking — the real catch
WSL2 runs containers in a **NAT'd VM behind Windows**, so by default they're not on
your LAN, and `network_mode: host` binds the *VM*, not the Windows NIC.

- **Windows 11 (recommended):** `networkingMode=mirrored` in `.wslconfig` makes WSL2
  share Windows' network interfaces. Host-mode containers then bind the real LAN IP and
  inbound LAN/router traffic works ≈ like native Linux — the whole `NETWORKING.md` model
  (DHCP reservation → router forward → UFW) applies to the **Windows** machine.
- **Windows 10 (no mirrored mode):** containers sit behind WSL2 NAT and you must forward
  Windows → WSL2 per port. ⚠️ **`netsh interface portproxy` is TCP-only** — it forwards
  Minecraft (`25565/tcp`) but **NOT** the UDP game ports (ARK `7777/7778`, Palworld/Steam
  `8211/27015`). For those you'd need a UDP relay (e.g. `socat` inside WSL), which is
  genuinely painful and the WSL2 IP changes on reboot. **Strongly prefer Win11 + mirrored.**

  TCP example (PowerShell as admin), for reference:
  ```powershell
  $wsl = (wsl hostname -I).Trim()
  netsh interface portproxy add v4tov4 listenport=25565 listenaddress=0.0.0.0 connectport=25565 connectaddress=$wsl
  New-NetFirewallRule -DisplayName "MC 25565" -Direction Inbound -Protocol TCP -LocalPort 25565 -Action Allow
  ```

### Migration steps
1. `git clone` into `~/` on WSL2 (config only — `server/`, `data/`, `.env` are gitignored).
2. `cp .env.example .env` and fill it (copy your secrets over from the laptop's `.env`).
3. First `./scripts/ctl.sh up <game>` re-downloads the game via steamcmd (~22 GB for ARK, etc.).
4. To keep progress, copy world saves from the laptop — e.g. ARK:
   `games/ark-se/server/server/ShooterGame/Saved/SavedArks/` and apply the
   `games/ark-se/*.ini.example` config (or copy the live `Config/LinuxServer/` files).
5. Redo the **router** DHCP reservation + port-forwards pointing at the **Windows** machine's IP.

### 24/7 notes
- Docker Desktop must stay running; Windows must **not sleep** (Power settings → never
  sleep). `host-setup.sh`'s lid-sleep/systemd logic is laptop-specific and N/A on Windows.
- WSL2 can idle-stop its VM; a running container keeps it alive.

**Bottom line:** smooth on **Windows 11 + mirrored networking**; on Windows 10 the
UDP-forwarding for ARK/Palworld is a headache — for a pure *disk* problem, an external
SSD on the laptop is often less hassle than re-solving networking on WSL2.
