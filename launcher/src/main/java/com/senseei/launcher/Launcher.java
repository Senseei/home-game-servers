package com.senseei.launcher;

import com.senseei.launcher.adapter.RepoRoot;
import com.senseei.launcher.adapter.ark.DotEnvStore;
import com.senseei.launcher.adapter.ark.FileConfigStore;
import com.senseei.launcher.adapter.ark.SteamWorkshopClient;
import com.senseei.launcher.adapter.ark.TsvModRegistry;
import com.senseei.launcher.adapter.docker.ComposeEngine;
import com.senseei.launcher.application.ServerLifecycle;
import com.senseei.launcher.application.ark.ArkMapService;
import com.senseei.launcher.application.ark.ModCatalogService;
import com.senseei.launcher.application.port.ConfigStore;
import com.senseei.launcher.application.port.ContainerEngine;
import com.senseei.launcher.application.port.EnvStore;
import com.senseei.launcher.application.port.ModRegistry;
import com.senseei.launcher.application.port.WorkshopClient;
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

        ConfigStore config = new FileConfigStore(root);
        EnvStore env = new DotEnvStore(root);
        ModRegistry registry = new TsvModRegistry(root);
        WorkshopClient workshop = new SteamWorkshopClient();
        ArkMapService arkMaps = new ArkMapService(config, env);
        ModCatalogService mods = new ModCatalogService(registry, workshop, config);

        return new CommandLine(new CtlCommand(lifecycle, arkMaps, mods))
                .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                    cmd.getErr().println("✗ " + ex.getMessage());
                    return 1;
                })
                .execute(args);
    }
}
