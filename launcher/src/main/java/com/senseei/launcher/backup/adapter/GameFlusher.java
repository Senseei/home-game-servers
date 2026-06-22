package com.senseei.launcher.backup.adapter;

import com.senseei.launcher.lifecycle.port.ContainerEngine;
import com.senseei.launcher.shared.port.EnvStore;
import com.senseei.launcher.backup.port.Flusher;
import com.senseei.launcher.backup.port.RconClient;
import com.senseei.launcher.lifecycle.domain.Game;

/**
 * Flushes a game's world to disk before a snapshot. Minecraft/Palworld via
 * {@code rcon-cli} in the container; ARK via direct RCON (the hermsi image has
 * no rcon-cli). Best-effort — a failure (server offline) only warns.
 */
public final class GameFlusher implements Flusher {

    private static final int ARK_RCON_PORT = 27020;
    private static final long SETTLE_MS = 3000;   // let the async save reach disk before we tar

    private final ContainerEngine engine;
    private final RconClient rcon;
    private final EnvStore env;

    public GameFlusher(ContainerEngine engine, RconClient rcon, EnvStore env) {
        this.engine = engine;
        this.rcon = rcon;
        this.env = env;
    }

    @Override
    public void flush(Game game) {
        try {
            switch (game.name()) {
                case "minecraft" -> engine.exec(game, "rcon-cli", "save-all", "flush");
                case "palworld" -> engine.exec(game, "rcon-cli", "Save");
                case "ark-se" -> rcon.execute(ARK_RCON_PORT, env.get("ADMIN_PASSWORD").orElse(""), "saveworld");
                default -> {
                    return;   // nothing to flush, nothing to settle
                }
            }
            Thread.sleep(SETTLE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            System.err.println("  (flush skipped — " + e.getMessage() + ")");
        }
    }
}
