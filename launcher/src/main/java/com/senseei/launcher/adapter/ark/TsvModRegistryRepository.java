package com.senseei.launcher.adapter.ark;

import com.senseei.launcher.application.port.ModRegistryRepository;
import com.senseei.launcher.domain.ark.Mod;
import com.senseei.launcher.domain.ark.ModRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Repository for the {@link ModRegistry} aggregate, backed by games/ark-se/mods.tsv. */
public final class TsvModRegistryRepository implements ModRegistryRepository {

    private static final String HEADER = """
            # ARK mod registry — Steam Workshop id <TAB> name.
            # Managed by the launcher; the per-map mod editor selects from this catalog.
            """;

    private final Path file;

    public TsvModRegistryRepository(Path root) {
        this.file = root.resolve("games/ark-se/mods.tsv");
    }

    @Override
    public ModRegistry load() {
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
        return new ModRegistry(mods);
    }

    @Override
    public void save(ModRegistry registry) {
        StringBuilder sb = new StringBuilder(HEADER);
        for (Mod m : registry.all()) {
            sb.append(m.id()).append('\t').append(m.name()).append('\n');
        }
        try {
            Files.writeString(file, sb.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> lines() {
        try {
            return Files.exists(file) ? Files.readAllLines(file) : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
