package com.senseei.launcher;

import com.senseei.launcher.adapter.RepoRoot;
import com.senseei.launcher.adapter.ark.DotEnvStore;
import com.senseei.launcher.adapter.ark.FileLiveConfig;
import com.senseei.launcher.adapter.ark.FileMapConfigRepository;
import com.senseei.launcher.adapter.ark.SteamWorkshopClient;
import com.senseei.launcher.adapter.ark.TsvModRegistryRepository;
import com.senseei.launcher.adapter.docker.ComposeEngine;
import com.senseei.launcher.application.ServerLifecycle;
import com.senseei.launcher.application.ark.ArkMapService;
import com.senseei.launcher.application.ark.ModCatalogService;
import com.senseei.launcher.application.port.ContainerEngine;
import com.senseei.launcher.application.port.EnvStore;
import com.senseei.launcher.application.port.LiveConfig;
import com.senseei.launcher.application.port.MapConfigRepository;
import com.senseei.launcher.application.port.ModRegistryRepository;
import com.senseei.launcher.application.port.WorkshopClient;
import com.senseei.launcher.adapter.backup.GameFlusher;
import com.senseei.launcher.adapter.backup.TarRcloneBackupStore;
import com.senseei.launcher.adapter.rcon.SourceRconClient;
import com.senseei.launcher.application.backup.BackupService;
import com.senseei.launcher.application.port.BackupStore;
import com.senseei.launcher.application.port.Flusher;
import com.senseei.launcher.application.port.RconClient;
import com.senseei.launcher.cli.CtlCommand;
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
        ModCatalogService mods = new ModCatalogService(registry, workshop, mapConfigs);

        RconClient rcon = new SourceRconClient();
        Flusher flusher = new GameFlusher(engine, rcon, env);
        BackupStore backupStore = new TarRcloneBackupStore(root, env);
        BackupService backups = new BackupService(root, flusher, backupStore, env, Clock.systemDefaultZone());

        return new CommandLine(new CtlCommand(lifecycle, arkMaps, mods, backups))
                .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                    cmd.getErr().println("✗ " + ex.getMessage());
                    return 1;
                })
                .execute(args);
    }
}
