package com.senseei.launcher.ark.adapter;

import com.senseei.launcher.ark.port.LiveConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** LiveConfig over the running server's config dir (.../Saved/Config/LinuxServer). */
public final class FileLiveConfig implements LiveConfig {

    private final Path dir;

    public FileLiveConfig(Path root) {
        this.dir = root.resolve("games/ark-se/server/server/ShooterGame/Saved/Config/LinuxServer");
    }

    @Override
    public String gameUserSettings() {
        Path f = dir.resolve("GameUserSettings.ini");
        try {
            return Files.exists(f) ? Files.readString(f) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(String gameUserSettings, String gameIni) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("GameUserSettings.ini"), gameUserSettings);
            Files.writeString(dir.resolve("Game.ini"), gameIni);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
