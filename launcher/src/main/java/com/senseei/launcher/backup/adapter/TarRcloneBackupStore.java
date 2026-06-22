package com.senseei.launcher.backup.adapter;

import com.senseei.launcher.backup.port.BackupStore;
import com.senseei.launcher.shared.port.EnvStore;
import com.senseei.launcher.lifecycle.domain.Game;
import com.senseei.launcher.backup.domain.Snapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Backup store: tar archives under {@code backups/<game>/}, mirrored to an rclone
 * remote ({@code RCLONE_REMOTE}). {@code tar -h} follows the data symlinks.
 */
public final class TarRcloneBackupStore implements BackupStore {

    private final Path root;
    private final EnvStore env;

    public TarRcloneBackupStore(Path root, EnvStore env) {
        this.root = root;
        this.env = env;
    }

    private Path dir(String game) {
        return root.resolve("backups").resolve(game);
    }

    private Path local(Snapshot s) {
        return dir(s.game()).resolve(s.fileName());
    }

    @Override
    public Snapshot archive(Game game, String fileName, Path sourceDir) {
        try {
            Files.createDirectories(dir(game.name()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Snapshot snapshot = new Snapshot(game.name(), fileName);
        int code = run("tar", "-czhf", local(snapshot).toString(),
                "-C", sourceDir.getParent().toString(), sourceDir.getFileName().toString());
        if (code > 1) {   // 1 = files changed while reading (benign autosave); >=2 is fatal
            throw new RuntimeException("tar failed (exit " + code + ")");
        }
        return snapshot;
    }

    @Override
    public List<Snapshot> localSnapshots(Game game) {
        Path d = dir(game.name());
        if (!Files.isDirectory(d)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(d)) {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".tar.gz"))
                    .sorted(Comparator.reverseOrder())
                    .map(n -> new Snapshot(game.name(), n))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void deleteLocal(Snapshot snapshot) {
        try {
            Files.deleteIfExists(local(snapshot));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void extract(Snapshot snapshot, Path destDir) {
        if (run("tar", "-xzf", local(snapshot).toString(), "-C", destDir.toString()) != 0) {
            throw new RuntimeException("tar extract failed");
        }
    }

    private Optional<String> remote() {
        return env.get("RCLONE_REMOTE").filter(s -> !s.isBlank());
    }

    private String remotePath(String game) {
        return remote().orElseThrow() + "/" + game + "/";
    }

    @Override
    public boolean offsiteEnabled() {
        return remote().isPresent() && which("rclone");
    }

    @Override
    public void pushOffsite(Snapshot snapshot) {
        runOrThrow("rclone", "copy", local(snapshot).toString(), remotePath(snapshot.game()), "--no-traverse");
    }

    @Override
    public List<String> offsiteSnapshots(Game game) {
        return capture("rclone", "lsf", remotePath(game.name()), "--files-only").lines()
                .filter(n -> n.endsWith(".tar.gz"))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    @Override
    public void deleteOffsite(Game game, String fileName) {
        runOrThrow("rclone", "deletefile", remotePath(game.name()) + fileName);
    }

    @Override
    public void pullOffsite(Game game, String fileName) {
        runOrThrow("rclone", "copy", remotePath(game.name()) + fileName, dir(game.name()).toString(), "--no-traverse");
    }

    private static int run(String... cmd) {
        try {
            return new ProcessBuilder(cmd).inheritIO().start().waitFor();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
    }

    private static void runOrThrow(String... cmd) {
        if (run(cmd) != 0) {
            throw new RuntimeException("command failed: " + String.join(" ", cmd));
        }
    }

    private static boolean which(String prog) {
        try {
            return new ProcessBuilder("which", prog).start().waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String capture(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
    }
}
