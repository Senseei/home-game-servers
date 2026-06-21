package com.senseei.launcher.application.port;

import com.senseei.launcher.domain.Game;

import java.nio.file.Path;
import java.util.List;

/** Port: offsite backup storage (an rclone remote). */
public interface OffsiteBackups {

    boolean enabled();

    void push(Game game, Path archive);

    /** Remote file names, newest first. */
    List<String> list(Game game);

    void delete(Game game, String fileName);

    void pull(Game game, String fileName, Path destDir);
}
