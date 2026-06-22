package com.senseei.launcher.backup.domain;

import java.util.Map;

/**
 * Per-game backup scope: the repo-relative directory to snapshot. ARK backs up
 * only the (small) Saved dir, not the 22 GB re-downloadable install.
 */
public final class BackupSpec {

    private static final Map<String, String> DATA_PATH = Map.of(
            "minecraft", "games/minecraft/data",
            "palworld", "games/palworld/data",
            "ark-se", "games/ark-se/server/server/ShooterGame/Saved");

    private BackupSpec() {
    }

    public static String dataPath(String game) {
        String path = DATA_PATH.get(game);
        if (path == null) {
            throw new IllegalArgumentException("no backup spec for game: " + game);
        }
        return path;
    }
}
