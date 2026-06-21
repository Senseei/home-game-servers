package com.senseei.launcher;

import com.senseei.launcher.adapter.RepoRoot;
import com.senseei.launcher.adapter.docker.ComposeEngine;
import com.senseei.launcher.application.ServerLifecycle;
import com.senseei.launcher.application.port.ContainerEngine;
import com.senseei.launcher.cli.CtlCommand;
import picocli.CommandLine;

import java.nio.file.Path;

/** Entry point: wires the dependency graph (root -> engine -> use-case) and runs the CLI. */
public final class Launcher {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    private static int run(String[] args) {
        Path root;
        try {
            root = RepoRoot.find();
        } catch (RuntimeException e) {
            System.err.println("✗ " + e.getMessage());
            return 1;
        }

        ContainerEngine engine = new ComposeEngine(root);
        ServerLifecycle lifecycle = new ServerLifecycle(engine);

        return new CommandLine(new CtlCommand(lifecycle))
                .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                    cmd.getErr().println("✗ " + ex.getMessage());
                    return 1;
                })
                .execute(args);
    }
}
