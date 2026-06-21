package com.senseei.launcher.adapter.ark;

import com.senseei.launcher.application.port.ConfigStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** ConfigStore over the repo's games/ark-se/{default,maps} and the live config dir. */
public final class FileConfigStore implements ConfigStore {

    private final Path defaults;
    private final Path maps;
    private final Path live;

    public FileConfigStore(Path root) {
        Path ark = root.resolve("games/ark-se");
        this.defaults = ark.resolve("default");
        this.maps = ark.resolve("maps");
        this.live = ark.resolve("server/server/ShooterGame/Saved/Config/LinuxServer");
    }

    private Path sourceDir(String source) {
        return source.equals("default") ? defaults : maps.resolve(source);
    }

    @Override
    public boolean mapExists(String map) {
        return Files.isDirectory(maps.resolve(map));
    }

    @Override
    public List<String> customMaps() {
        if (!Files.isDirectory(maps)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(maps)) {
            return s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void copyDefaultTo(String map) {
        Path dest = maps.resolve(map);
        try {
            Files.createDirectories(dest);
            for (String f : List.of("GameUserSettings.ini", "Game.ini", "mods")) {
                Files.copy(defaults.resolve(f), dest.resolve(f));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void deleteMap(String map) {
        Path dir = maps.resolve(map);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String readGameUserSettings(String source) {
        return read(sourceDir(source).resolve("GameUserSettings.ini"));
    }

    @Override
    public String readGameIni(String source) {
        return read(sourceDir(source).resolve("Game.ini"));
    }

    @Override
    public List<String> readModIds(String source) {
        return read(sourceDir(source).resolve("mods")).lines()
                .map(line -> {
                    int hash = line.indexOf('#');
                    return (hash >= 0 ? line.substring(0, hash) : line).strip();
                })
                .filter(s -> s.matches("\\d+"))
                .toList();
    }

    @Override
    public void writeModIds(String source, List<String> ids) {
        StringBuilder sb = new StringBuilder("# " + source + " mods\n");
        ids.forEach(id -> sb.append(id).append('\n'));
        write(sourceDir(source).resolve("mods"), sb.toString());
    }

    @Override
    public String readLiveGameUserSettings() {
        return read(live.resolve("GameUserSettings.ini"));
    }

    @Override
    public void writeLiveGameUserSettings(String content) {
        write(live.resolve("GameUserSettings.ini"), content);
    }

    @Override
    public void writeLiveGameIni(String content) {
        write(live.resolve("Game.ini"), content);
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path p, String content) {
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
