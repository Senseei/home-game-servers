package com.senseei.launcher.cli;

import com.senseei.launcher.application.ServerLifecycle;
import com.senseei.launcher.application.ServerStatus;
import com.senseei.launcher.application.ark.ArkMapService;
import com.senseei.launcher.application.ark.ModCatalogService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.InputStream;
import java.util.concurrent.Callable;

/**
 * Thin presentation adapter (Picocli). The full UI/UX is a later, separate
 * phase; this is the scriptable entry meanwhile. Subcommands reach the
 * use-case via {@link ParentCommand}, which Picocli injects.
 */
@Command(
        name = "ctl",
        description = "Game server control (Java rewrite · phase 1: lifecycle).",
        mixinStandardHelpOptions = true,
        version = "ctl 0.1.0",
        subcommands = {
                CtlCommand.Up.class,
                CtlCommand.Down.class,
                CtlCommand.Restart.class,
                CtlCommand.Logs.class,
                CtlCommand.Status.class,
                ArkCommand.class
        }
)
public final class CtlCommand {

    final ServerLifecycle lifecycle;
    final ArkMapService arkMaps;
    final ModCatalogService mods;

    public CtlCommand(ServerLifecycle lifecycle, ArkMapService arkMaps, ModCatalogService mods) {
        this.lifecycle = lifecycle;
        this.arkMaps = arkMaps;
        this.mods = mods;
    }

    @Command(name = "up", description = "Start a server.")
    static final class Up implements Callable<Integer> {
        @ParentCommand CtlCommand parent;
        @Parameters(index = "0", paramLabel = "<game>") String game;

        @Override
        public Integer call() {
            parent.lifecycle.start(game);
            System.out.println("✓ " + game + " started");
            return 0;
        }
    }

    @Command(name = "down", description = "Stop a server.")
    static final class Down implements Callable<Integer> {
        @ParentCommand CtlCommand parent;
        @Parameters(index = "0", paramLabel = "<game>") String game;

        @Override
        public Integer call() {
            parent.lifecycle.stop(game);
            System.out.println("✓ " + game + " stopped");
            return 0;
        }
    }

    @Command(name = "restart", description = "Restart a server.")
    static final class Restart implements Callable<Integer> {
        @ParentCommand CtlCommand parent;
        @Parameters(index = "0", paramLabel = "<game>") String game;

        @Override
        public Integer call() {
            parent.lifecycle.restart(game);
            return 0;
        }
    }

    @Command(name = "logs", description = "Follow a server's logs.")
    static final class Logs implements Callable<Integer> {
        @ParentCommand CtlCommand parent;
        @Parameters(index = "0", paramLabel = "<game>") String game;

        @Override
        public Integer call() throws Exception {
            try (InputStream logs = parent.lifecycle.logs(game, true, 100)) {
                logs.transferTo(System.out);
            }
            return 0;
        }
    }

    @Command(name = "status", description = "Show every server's state.")
    static final class Status implements Callable<Integer> {
        @ParentCommand CtlCommand parent;

        @Override
        public Integer call() {
            System.out.printf("%-12s %s%n", "GAME", "STATE");
            for (ServerStatus s : parent.lifecycle.status()) {
                System.out.printf("%-12s %s%n", s.game(), s.state().name().toLowerCase());
            }
            return 0;
        }
    }
}
