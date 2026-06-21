# launcher — Java rewrite of the control tool

A hexagonal / clean-architecture rewrite of the bash `ctl.sh` / `ark.sh` launcher,
built incrementally. **The bash scripts remain the working tool** until this reaches
parity. Plain Java 21 + Picocli, built with Maven.

## Layers (your Controller → UseCase → Service shape)
| Package | Role |
|---|---|
| `domain` | entities + rules, **no I/O**, unit-tested (`Game`, `GameCatalog`; later: ArkMap inheritance, the `[ServerSettings]` merge) |
| `application` | use-cases (`ServerLifecycle`) — **return data, never formatted text** |
| `application.port` | interfaces the use-cases depend on (`ContainerEngine`, later `RconClient`, `ConfigStore`, …) |
| `adapter` | implementations — `docker.ComposeEngine` shells out to `docker compose` (wraps it, doesn't reimplement it) |
| `cli` | thin presentation (Picocli); the **real UI/UX is a later phase** |
| `Launcher` | entry point — wires the graph |

Presentation is fully decoupled — use-cases take/return data — so the UI can be
swapped (CLI → TUI → web) without touching business logic. The domain and
application layers are **framework-free** (no Spring), so they stay portable and
fast to test; Spring can arrive later as a web presentation adapter if wanted.

## Build & run
```
cd launcher
mvn clean package          # compile + test + build target/ctl.jar
java -jar target/ctl.jar status
```
A later step adds a GraalVM `native-image` build for an instant single binary.

## Migration phases
1. **done** — lifecycle: `up | down | restart | logs | status`.
2. ARK domain: maps inherit/custom + the `[ServerSettings]` merge (with tests),
   `ConfigStore` / `WorkshopClient` / `RconClient` adapters — ark.sh parity.
3. Backups: RCON flush → tar → rotate → rclone.
4. UI/UX — a clean-slate presentation over the stable core.
5. Retire the bash scripts; `./ctl` becomes this; CI swaps shellcheck → `mvn test`.
