# launcher — Java rewrite of the control tool

A hexagonal / clean-architecture rewrite of the bash `ctl.sh` / `ark.sh` launcher,
built incrementally. **The bash scripts remain the working tool** until this reaches
parity. Plain Java 21 + Picocli, built with Maven.

## Structure — feature-first, layered within
Each feature owns a vertical slice; inside it the clean-arch layers are explicit
sub-packages — so you navigate by **feature** (which folder) and see the **layer** at a
glance (which file does what):

```
lifecycle/  domain · app · port · adapter    Game · ServerLifecycle · ContainerEngine · ComposeEngine
ark/        domain · app · port · adapter    MapConfig·IniMerge · ArkMapService · repos · File*/Steam*
backup/     domain · app · port · adapter    BackupPolicy · BackupService · BackupStore · Tar·RCON
shared/     port · adapter                   EnvStore · DotEnvStore · RepoRoot
cli/                                         thin Picocli presentation (real UI/UX is a later phase)
Launcher                                     composition root — wires the graph
```

Within a feature: **`domain/`** = entities, value objects, domain services (no I/O,
unit-tested); **`app/`** = use-cases (return data, never text); **`port/`** = the interfaces
the use-cases depend on; **`adapter/`** = the implementations. Domain + app are
framework-free (no Spring); the presentation is fully decoupled, so the UI swaps
CLI → TUI → web without touching the slices.

## Build & run
```
cd launcher
mvn clean package          # compile + test + build target/ctl.jar
java -jar target/ctl.jar            # no args → interactive arrow-key menu, cli/Shell
java -jar target/ctl.jar status     # with args → one-shot command (Picocli), cli/
# or just ./ctl  /  ./ctl status   (wrapper at the repo root)
```
A later step adds a GraalVM `native-image` build for an instant single binary.

## Migration phases
1. **done** — lifecycle: `up | down | restart | logs | status`.
2. **done** — ARK domain (`ctl ark …`): map inherit/custom + the `[ServerSettings]`
   `IniMerge` (pure, 7 tests); `ConfigStore`/`ModRegistry`/`WorkshopClient`/`EnvStore`
   ports + adapters; `ArkMapService`/`ModCatalogService` use-cases — ark.sh parity.
3. **done** — backups (`ctl backup|restore`): `BackupPolicy` (pure rotation), a Java
   Source-RCON flush (replaces rcon.py), tar, local + rclone offsite. Core complete.
4. **done** — UI/UX: an interactive arrow-key menu (`cli/Shell` + `Chooser`/`Toggler`)
   over the use-cases; `ctl` no-args opens it, `ctl <cmd>` stays the CLI.
5. **done** — retired the bash scripts; `./ctl` is this; CI runs `mvn package`.
