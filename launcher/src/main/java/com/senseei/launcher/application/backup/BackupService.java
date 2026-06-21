package com.senseei.launcher.application.backup;

import com.senseei.launcher.application.port.Archiver;
import com.senseei.launcher.application.port.EnvStore;
import com.senseei.launcher.application.port.Flusher;
import com.senseei.launcher.application.port.LocalBackups;
import com.senseei.launcher.application.port.OffsiteBackups;
import com.senseei.launcher.domain.Game;
import com.senseei.launcher.domain.GameCatalog;
import com.senseei.launcher.domain.backup.BackupPolicy;
import com.senseei.launcher.domain.backup.BackupSpec;
import com.senseei.launcher.domain.backup.Snapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Backup + restore use-cases: flush → archive → rotate (local + offsite), and
 * restore a snapshot. Rotation is the {@link BackupPolicy} domain rule.
 */
public final class BackupService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path root;
    private final Flusher flusher;
    private final Archiver archiver;
    private final LocalBackups local;
    private final OffsiteBackups offsite;
    private final EnvStore env;
    private final Clock clock;

    public BackupService(Path root, Flusher flusher, Archiver archiver, LocalBackups local,
                         OffsiteBackups offsite, EnvStore env, Clock clock) {
        this.root = root;
        this.flusher = flusher;
        this.archiver = archiver;
        this.local = local;
        this.offsite = offsite;
        this.env = env;
        this.clock = clock;
    }

    public Snapshot backup(String gameName) {
        Game game = GameCatalog.lookup(gameName);

        flusher.flush(game);

        String fileName = gameName + "-" + LocalDateTime.now(clock).format(STAMP) + ".tar.gz";
        Path archive = local.pathFor(game, fileName);
        archiver.create(root.resolve(BackupSpec.dataPath(gameName)), archive);

        for (Snapshot stale : BackupPolicy.surplus(local.list(game), keep("BACKUP_KEEP_LOCAL", 7))) {
            local.delete(stale);
        }

        if (offsite.enabled()) {
            offsite.push(game, archive);
            for (String stale : BackupPolicy.surplus(offsite.list(game), keep("BACKUP_KEEP_REMOTE", 14))) {
                offsite.delete(game, stale);
            }
        }
        return new Snapshot(gameName, fileName);
    }

    public void restore(String gameName, String which) {
        Game game = GameCatalog.lookup(gameName);
        Snapshot snapshot = locate(game, which);
        Path archive = local.resolve(snapshot);
        if (!Files.exists(archive) && offsite.enabled()) {
            offsite.pull(game, snapshot.fileName(), local.pathFor(game, snapshot.fileName()).getParent());
        }
        archiver.extract(archive, root.resolve(BackupSpec.dataPath(gameName)).getParent());
    }

    private Snapshot locate(Game game, String which) {
        List<Snapshot> snapshots = local.list(game);
        if (which == null || which.equals("latest")) {
            if (snapshots.isEmpty()) {
                throw new IllegalStateException("no local backups for " + game.name());
            }
            return snapshots.get(0);
        }
        return snapshots.stream().filter(s -> s.fileName().equals(which)).findFirst()
                .orElse(new Snapshot(game.name(), which));   // maybe offsite-only
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
