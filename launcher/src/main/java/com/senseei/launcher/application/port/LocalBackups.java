package com.senseei.launcher.application.port;

import com.senseei.launcher.domain.Game;
import com.senseei.launcher.domain.backup.Snapshot;

import java.nio.file.Path;
import java.util.List;

/** Port: the local backup directory for a game. */
public interface LocalBackups {

    /** Where a new archive with {@code fileName} should be written. */
    Path pathFor(Game game, String fileName);

    /** Existing snapshots, newest first. */
    List<Snapshot> list(Game game);

    Path resolve(Snapshot snapshot);

    void delete(Snapshot snapshot);
}
