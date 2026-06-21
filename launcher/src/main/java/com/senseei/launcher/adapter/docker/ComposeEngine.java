package com.senseei.launcher.adapter.docker;

import com.senseei.launcher.application.port.ContainerEngine;
import com.senseei.launcher.domain.Game;
import com.senseei.launcher.domain.RunState;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ContainerEngine adapter: wraps {@code docker compose} (and the repo's compose
 * files + root .env). We orchestrate via compose, we don't reimplement it.
 */
public final class ComposeEngine implements ContainerEngine {

    private final Path root;

    public ComposeEngine(Path root) {
        this.root = root;
    }

    private List<String> base(Game game) {
        return new ArrayList<>(List.of(
                "docker", "compose",
                "--env-file", root.resolve(".env").toString(),
                "-f", root.resolve(game.composeDir()).resolve("compose.yaml").toString()
        ));
    }

    private void run(Game game, String... args) {
        List<String> cmd = base(game);
        cmd.addAll(Arrays.asList(args));
        try {
            int code = new ProcessBuilder(cmd).inheritIO().start().waitFor();
            if (code != 0) {
                throw new RuntimeException("docker compose " + String.join(" ", args)
                        + " failed for " + game.name() + " (exit " + code + ")");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
    }

    @Override
    public void up(Game game) {
        run(game, "up", "-d");
    }

    @Override
    public void down(Game game) {
        run(game, "down");
    }

    @Override
    public void restart(Game game) {
        run(game, "restart");
    }

    @Override
    public InputStream logs(Game game, boolean follow, int tail) {
        List<String> cmd = base(game);
        cmd.add("logs");
        cmd.add("--tail=" + tail);
        if (follow) {
            cmd.add("-f");
        }
        try {
            Process p = new ProcessBuilder(cmd)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            // Wrap so closing the stream also reaps the child process.
            return new FilterInputStream(p.getInputStream()) {
                @Override
                public void close() throws IOException {
                    super.close();
                    p.destroy();
                }
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public RunState state(Game game) {
        try {
            Process p = new ProcessBuilder("docker", "ps", "--format", "{{.Names}}")
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            boolean running = out.lines().anyMatch(line -> line.equals(game.container()));
            return running ? RunState.RUNNING : RunState.STOPPED;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
    }
}
