package com.senseei.launcher.adapter.backup;

import com.senseei.launcher.application.port.EnvStore;
import com.senseei.launcher.application.port.OffsiteBackups;
import com.senseei.launcher.domain.Game;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Offsite backups via the {@code rclone} CLI ({@code RCLONE_REMOTE} in .env). */
public final class RcloneOffsite implements OffsiteBackups {

    private final EnvStore env;

    public RcloneOffsite(EnvStore env) {
        this.env = env;
    }

    private Optional<String> remote() {
        return env.get("RCLONE_REMOTE").filter(s -> !s.isBlank());
    }

    private String path(Game game) {
        return remote().orElseThrow() + "/" + game.name() + "/";
    }

    @Override
    public boolean enabled() {
        return remote().isPresent() && which("rclone");
    }

    @Override
    public void push(Game game, Path archive) {
        run("rclone", "copy", archive.toString(), path(game), "--no-traverse");
    }

    @Override
    public List<String> list(Game game) {
        return capture("rclone", "lsf", path(game), "--files-only").lines()
                .filter(n -> n.endsWith(".tar.gz"))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    @Override
    public void delete(Game game, String fileName) {
        run("rclone", "deletefile", path(game) + fileName);
    }

    @Override
    public void pull(Game game, String fileName, Path destDir) {
        run("rclone", "copy", path(game) + fileName, destDir.toString(), "--no-traverse");
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

    private static void run(String... cmd) {
        try {
            int code = new ProcessBuilder(cmd).inheritIO().start().waitFor();
            if (code != 0) {
                throw new RuntimeException("rclone failed (exit " + code + "): " + String.join(" ", cmd));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
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
