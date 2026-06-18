# Palworld — importing an existing world

This covers migrating a world from another machine into the Dockerized server.
Paths assume the compose mount `./data:/palworld`, so container `/palworld` =
host `games/palworld/data/`.

## Save layout

A world save is a folder named with a 32-char hex `<WORLD_ID>` containing
`Level.sav`, `LevelMeta.sav`, `WorldOption.sav`, and a `Players/` folder. Here it
lives at:

```
games/palworld/data/Pal/Saved/SaveGames/0/<WORLD_ID>/
```

## Source path (Windows dedicated server → here)

A save from a **Windows PalServer dedicated server** is a clean server-to-server
move — **no conversion needed**. Grab the whole world folder from:

```
...\PalServer\Pal\Saved\SaveGames\0\<WORLD_ID>\
```

> If the world had instead been hosted **in-game (co-op/invite)**, the host
> character (`Players/00000001.sav`) would need conversion with
> [`cheahjs/palworld-save-tools`](https://github.com/cheahjs/palworld-save-tools)
> before a dedicated server could use it. The dedicated-server case below skips that.

## Migration steps

1. **First boot** the Docker server once so it builds its directory tree, then
   stop it: `make up GAME=palworld` → wait → `make down GAME=palworld`.
2. **Back up** the current `games/palworld/data/` before changing anything.
3. **Copy** the whole `<WORLD_ID>/` folder into
   `games/palworld/data/Pal/Saved/SaveGames/0/<WORLD_ID>/`.
4. **Point the server at it** — in
   `games/palworld/data/Pal/Saved/Config/LinuxServer/GameUserSettings.ini` set:
   ```
   DedicatedServerName=<WORLD_ID>
   ```
   (must match the folder name; the server loads the world named here, not just
   "whatever is in `SaveGames/0/`").
5. **Fix ownership** so the container (PUID/PGID, default 1000) can read/write:
   ```bash
   sudo chown -R 1000:1000 games/palworld/data/
   ```
6. **Start**: `make up GAME=palworld`. If a fresh world appears instead of yours,
   `DedicatedServerName` doesn't match the folder name.

## ⚠️ Settings file gotcha

This image **regenerates `PalWorldSettings.ini` from your compose env vars on
every boot** — pasting your old settings ini won't stick. Instead:

- Translate the settings you care about into the compose `environment:` block
  (most Palworld settings have env-var equivalents), **or**
- set `DISABLE_GENERATE_SETTINGS: "true"` to hand-manage the ini yourself.

Your world's baked-in options ride along in `WorldOption.sav` inside the save
folder regardless.
