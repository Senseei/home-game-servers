package com.senseei.launcher.adapter.ark;

import com.senseei.launcher.application.port.EnvStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** EnvStore over the root {@code .env}: replaces a KEY= line in place, or appends it. */
public final class DotEnvStore implements EnvStore {

    private final Path env;

    public DotEnvStore(Path root) {
        this.env = root.resolve(".env");
    }

    @Override
    public Optional<String> get(String key) {
        String prefix = key + "=";
        return lines().stream()
                .filter(l -> l.startsWith(prefix))
                .map(l -> l.substring(prefix.length()))
                .findFirst();
    }

    @Override
    public void set(String key, String value) {
        String prefix = key + "=";
        List<String> lines = new ArrayList<>(lines());
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                lines.set(i, key + "=" + value);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            lines.add(key + "=" + value);
        }
        try {
            Files.writeString(env, String.join("\n", lines) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> lines() {
        try {
            return Files.exists(env) ? Files.readAllLines(env) : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
