package com.senseei.launcher;

import com.senseei.launcher.shared.adapter.RepoRoot;
import com.senseei.launcher.shared.adapter.DotEnvStore;
import com.senseei.launcher.ark.adapter.FileLiveConfig;
import com.senseei.launcher.ark.adapter.FileMapConfigRepository;
import com.senseei.launcher.ark.adapter.SteamWorkshopClient;
import com.senseei.launcher.ark.adapter.TsvModRegistryRepository;
import com.senseei.launcher.lifecycle.adapter.ComposeEngine;
import com.senseei.launcher.lifecycle.app.ServerLifecycle;
import com.senseei.launcher.ark.app.ArkMapService;
import com.senseei.launcher.ark.app.ModCatalogService;
import com.senseei.launcher.lifecycle.port.ContainerEngine;
import com.senseei.launcher.shared.port.EnvStore;
import com.senseei.launcher.ark.port.LiveConfig;
import com.senseei.launcher.ark.port.MapConfigRepository;
import com.senseei.launcher.ark.port.ModRegistryRepository;
import com.senseei.launcher.ark.port.WorkshopClient;
import com.senseei.launcher.backup.adapter.GameFlusher;
import com.senseei.launcher.backup.adapter.TarRcloneBackupStore;
import com.senseei.launcher.backup.adapter.SourceRconClient;
import com.senseei.launcher.backup.app.BackupService;
import com.senseei.launcher.backup.port.BackupStore;
import com.senseei.launcher.backup.port.Flusher;
import com.senseei.launcher.backup.port.RconClient;
import com.senseei.launcher.cli.CtlCommand;
import com.senseei.launcher.cli.Shell;
import picocli.CommandLine;

import java.nio.file.Path;
import java.time.Clock;

/** Entry point: wires the dependency graph (root → adapters → use-cases) and runs the CLI. */
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

        MapConfigRepository mapConfigs = new FileMapConfigRepository(root);
        LiveConfig live = new FileLiveConfig(root);
        EnvStore env = new DotEnvStore(root);
        ModRegistryRepository registry = new TsvModRegistryRepository(root);
        WorkshopClient workshop = new SteamWorkshopClient();
        ArkMapService arkMaps = new ArkMapService(mapConfigs, live, env);
        ModCatalogService mods = new ModCatalogService(registry, workshop, mapConfigs, env);

        RconClient rcon = new SourceRconClient();
        Flusher flusher = new GameFlusher(engine, rcon, env);
        BackupStore backupStore = new TarRcloneBackupStore(root, env);
        BackupService backups = new BackupService(root, flusher, backupStore, env, Clock.systemDefaultZone());

        CommandLine cmd = new CommandLine(new CtlCommand(lifecycle, arkMaps, mods, backups))
                .setExecutionExceptionHandler((ex, c, parseResult) -> {
                    c.getErr().println("✗ " + ex.getMessage());
                    return 1;
                });

        if (args.length == 0) {   // no args → interactive menu; otherwise a one-shot command
            try {
                new Shell(lifecycle, arkMaps, mods, backups).run();
                return 0;
            } catch (java.io.IOException e) {
                System.err.println("✗ " + e.getMessage());
                return 1;
            }
        }
        return cmd.execute(args);
    }
}
