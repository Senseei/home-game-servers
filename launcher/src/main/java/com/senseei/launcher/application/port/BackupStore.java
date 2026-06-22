package com.senseei.launcher.application.port;

import com.senseei.launcher.domain.Game;
import com.senseei.launcher.domain.backup.Snapshot;

import java.nio.file.Path;
import java.util.List;

/**
 * Port: backup persistence — make and keep archives locally and offsite. One
 * concept ("where backups live + how they're made"), replacing the old
 * Archiver + LocalBackups + OffsiteBackups split.
 */
public interface BackupStore {

    /** Archive {@code sourceDir} into the game's local backup area. */
    Snapshot archive(Game game, String fileName, Path sourceDir);

    /** Local snapshots, newest first. */
    List<Snapshot> localSnapshots(Game game);

    void deleteLocal(Snapshot snapshot);

    void extract(Snapshot snapshot, Path destDir);

    boolean offsiteEnabled();

    void pushOffsite(Snapshot snapshot);

    /** Offsite file names, newest first. */
    List<String> offsiteSnapshots(Game game);

    void deleteOffsite(Game game, String fileName);

    void pullOffsite(Game game, String fileName);
}
