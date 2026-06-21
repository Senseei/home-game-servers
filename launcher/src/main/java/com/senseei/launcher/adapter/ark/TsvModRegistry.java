package com.senseei.launcher.adapter.ark;

import com.senseei.launcher.application.port.ModRegistry;
import com.senseei.launcher.domain.ark.Mod;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** ModRegistry backed by games/ark-se/mods.tsv ({@code id<TAB>name} per line). */
public final class TsvModRegistry implements ModRegistry {

    private final Path file;

    public TsvModRegistry(Path root) {
        this.file = root.resolve("games/ark-se/mods.tsv");
    }

    @Override
    public List<Mod> all() {
        List<Mod> mods = new ArrayList<>();
        for (String line : lines()) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", 2);
            if (parts.length == 2 && parts[0].matches("\\d+")) {
                mods.add(new Mod(parts[0], parts[1].strip()));
            }
        }
        return mods;
    }

    @Override
    public boolean contains(String id) {
        return all().stream().anyMatch(m -> m.id().equals(id));
    }

    @Override
    public void add(Mod mod) {
        try {
            Files.writeString(file, mod.id() + "\t" + mod.name() + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String nameOf(String id) {
        return all().stream().filter(m -> m.id().equals(id)).map(Mod::name).findFirst().orElse(id);
    }

    private List<String> lines() {
        try {
            return Files.exists(file) ? Files.readAllLines(file) : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
