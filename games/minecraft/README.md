# Minecraft — CurseForge modpack server

This server runs a **CurseForge modpack** via the image's `TYPE=AUTO_CURSEFORGE`.
The pack is a Forge/Fabric bundle, so:

- ⚠️ **Every player must install the *same* modpack client-side** (same loader +
  mods) to connect. Vanilla clients cannot join.
- Paper/Bukkit **plugins do not work** here — it's a modded server, not Paper.

## Setup

1. **Get a CurseForge API key** (free): <https://console.curseforge.com> →
   *My API Keys* → copy the key.
2. In `.env`, set it — the key contains `$`, so **wrap it in single quotes**:
   ```
   CF_API_KEY='$2a$10$abcd...'
   ```
3. **Pick a modpack** and set its page URL or slug in `.env`:
   ```
   MC_CF_MODPACK=https://www.curseforge.com/minecraft/modpacks/all-the-mods-10
   # or just the slug:
   MC_CF_MODPACK=all-the-mods-10
   ```
   The pack determines the MC version and mod loader automatically — don't set a
   version yourself.
4. Set `MC_MEMORY` to suit the pack (heavy packs want 6–8G+).
5. Launch: `make up GAME=minecraft` (first boot downloads the pack — be patient).

## Pin a specific pack version

By default the newest pack file is used. To pin a version, append a file id or
use the file-specific page URL from CurseForge (the *Files* tab → a version →
copy URL) as `MC_CF_MODPACK`. Pinning avoids a surprise pack update on restart.

## Adding individual mods on top

`AUTO_CURSEFORGE` manages the pack's mods under `./data/mods`. You can drop extra
**server-compatible** `.jar` mods into `games/minecraft/data/mods/` — but anything
that changes gameplay must also be installed by every client, or they'll fail to
join.

## Notes

- `MAX_TICK_TIME: "-1"` is set in the compose **because** autopause is on: without
  it, the modded watchdog kills the server when it resumes from a pause.
- Switching back to a plugin server later means setting `TYPE: PAPER` and using
  `MODRINTH_PROJECTS` / `CURSEFORGE_FILES` instead of a modpack.
