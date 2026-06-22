package com.senseei.launcher.backup.app;

import com.senseei.launcher.backup.port.BackupStore;
import com.senseei.launcher.shared.port.EnvStore;
import com.senseei.launcher.backup.port.Flusher;
import com.senseei.launcher.lifecycle.domain.Game;
import com.senseei.launcher.lifecycle.domain.GameCatalog;
import com.senseei.launcher.backup.domain.BackupPolicy;
import com.senseei.launcher.backup.domain.BackupSpec;
import com.senseei.launcher.backup.domain.Snapshot;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Backup + restore use-cases: flush → archive → rotate (local + offsite), and
 * restore. Rotation is the {@link BackupPolicy} domain rule.
 */
public final class BackupService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path root;
    private final Flusher flusher;
    private final BackupStore store;
    private final EnvStore env;
    private final Clock clock;

    public BackupService(Path root, Flusher flusher, BackupStore store, EnvStore env, Clock clock) {
        this.root = root;
        this.flusher = flusher;
        this.store = store;
        this.env = env;
        this.clock = clock;
    }

    public Snapshot backup(String gameName) {
        Game game = GameCatalog.lookup(gameName);

        flusher.flush(game);

        String fileName = gameName + "-" + LocalDateTime.now(clock).format(STAMP) + ".tar.gz";
        Snapshot snapshot = store.archive(game, fileName, root.resolve(BackupSpec.dataPath(gameName)));

        for (Snapshot stale : BackupPolicy.surplus(store.localSnapshots(game), keep("BACKUP_KEEP_LOCAL", 7))) {
            store.deleteLocal(stale);
        }
        if (store.offsiteEnabled()) {
            store.pushOffsite(snapshot);
            for (String stale : BackupPolicy.surplus(store.offsiteSnapshots(game), keep("BACKUP_KEEP_REMOTE", 14))) {
                store.deleteOffsite(game, stale);
            }
        }
        return snapshot;
    }

    public void restore(String gameName, String which) {
        Game game = GameCatalog.lookup(gameName);
        Snapshot snapshot = locate(game, which);
        boolean haveLocal = store.localSnapshots(game).stream().anyMatch(s -> s.fileName().equals(snapshot.fileName()));
        if (!haveLocal && store.offsiteEnabled()) {
            store.pullOffsite(game, snapshot.fileName());
        }
        store.extract(snapshot, root.resolve(BackupSpec.dataPath(gameName)).getParent());
    }

    private Snapshot locate(Game game, String which) {
        List<Snapshot> snapshots = store.localSnapshots(game);
        if (which == null || which.equals("latest")) {
            if (snapshots.isEmpty()) {
                throw new IllegalStateException("no local backups for " + game.name());
            }
            return snapshots.get(0);
        }
        return snapshots.stream().filter(s -> s.fileName().equals(which)).findFirst()
                .orElse(new Snapshot(game.name(), which));
    }

    private int keep(String envKey, int fallback) {
        return env.get(envKey).map(s -> {
            try {
                return Integer.parseInt(s.strip());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }).orElse(fallback);
    }
}
