package com.senseei.launcher.adapter.ark;

import com.senseei.launcher.application.port.MapConfigRepository;
import com.senseei.launcher.domain.ark.MapConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Repository for {@link MapConfig}, over games/ark-se/{default,maps}/. */
public final class FileMapConfigRepository implements MapConfigRepository {

    private static final String DEFAULT = "default";

    private final Path defaults;
    private final Path maps;

    public FileMapConfigRepository(Path root) {
        Path ark = root.resolve("games/ark-se");
        this.defaults = ark.resolve("default");
        this.maps = ark.resolve("maps");
    }

    private Path dir(String target) {
        return target.equals(DEFAULT) ? defaults : maps.resolve(target);
    }

    @Override
    public MapConfig load(String target) {
        boolean custom = !target.equals(DEFAULT) && exists(target);
        Path src = custom ? maps.resolve(target) : defaults;
        return new MapConfig(target, custom,
                read(src.resolve("GameUserSettings.ini")),
                read(src.resolve("Game.ini")),
                parseMods(read(src.resolve("mods"))));
    }

    @Override
    public boolean exists(String map) {
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
    public void save(MapConfig config) {
        Path d = dir(config.name());
        write(d.resolve("GameUserSettings.ini"), config.gameUserSettings());
        write(d.resolve("Game.ini"), config.gameIni());
        StringBuilder mods = new StringBuilder("# " + config.name() + " mods\n");
        config.modIds().forEach(id -> mods.append(id).append('\n'));
        write(d.resolve("mods"), mods.toString());
    }

    @Override
    public void delete(String map) {
        Path d = maps.resolve(map);
        if (!Files.exists(d)) {
            return;
        }
        try (Stream<Path> s = Files.walk(d)) {
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

    private static List<String> parseMods(String content) {
        return content.lines()
                .map(line -> {
                    int hash = line.indexOf('#');
                    return (hash >= 0 ? line.substring(0, hash) : line).strip();
                })
                .filter(s -> s.matches("\\d+"))
                .toList();
    }

    private static String read(Path p) {
        try {
            return Files.exists(p) ? Files.readString(p) : "";
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
