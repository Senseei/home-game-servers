package com.senseei.launcher.adapter.backup;

import com.senseei.launcher.application.port.LocalBackups;
import com.senseei.launcher.domain.Game;
import com.senseei.launcher.domain.backup.Snapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Local backups under {@code backups/<game>/}; newest-first by timestamped file name. */
public final class FileLocalBackups implements LocalBackups {

    private final Path root;

    public FileLocalBackups(Path root) {
        this.root = root;
    }

    private Path dir(String game) {
        return root.resolve("backups").resolve(game);
    }

    @Override
    public Path pathFor(Game game, String fileName) {
        try {
            Files.createDirectories(dir(game.name()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir(game.name()).resolve(fileName);
    }

    @Override
    public List<Snapshot> list(Game game) {
        Path dir = dir(game.name());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
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
    public Path resolve(Snapshot snapshot) {
        return dir(snapshot.game()).resolve(snapshot.fileName());
    }

    @Override
    public void delete(Snapshot snapshot) {
        try {
            Files.deleteIfExists(resolve(snapshot));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
