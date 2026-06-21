package com.senseei.launcher.cli;

import com.senseei.launcher.application.ark.ArkMapService;
import com.senseei.launcher.application.ark.MapChoice;
import com.senseei.launcher.application.ark.ModCatalogService;
import com.senseei.launcher.domain.ark.Mod;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/** ARK per-map config + mods. Subcommands reach the use-cases via the CtlCommand parent. */
@Command(name = "ark", description = "ARK per-map config + mods.",
        subcommands = {
                ArkCommand.Maps.class, ArkCommand.Apply.class, ArkCommand.Customize.class,
                ArkCommand.Uncustomize.class, ArkCommand.ListMods.class, ArkCommand.AddMod.class,
                ArkCommand.SyncMods.class, ArkCommand.SetMods.class
        })
public final class ArkCommand {

    @ParentCommand CtlCommand ctl;

    ArkMapService maps() { return ctl.arkMaps; }

    ModCatalogService mods() { return ctl.mods; }

    @Command(name = "maps", description = "List selectable maps (official + customized).")
    static final class Maps implements Callable<Integer> {
        @ParentCommand ArkCommand ark;

        @Override
        public Integer call() {
            for (MapChoice c : ark.maps().selectable()) {
                System.out.printf("%-16s %s%n", c.name(), c.custom() ? "● custom" : "○ default");
            }
            return 0;
        }
    }

    @Command(name = "apply", description = "Load a map's config + mods into the live server.")
    static final class Apply implements Callable<Integer> {
        @ParentCommand ArkCommand ark;
        @Parameters(index = "0", paramLabel = "<map>") String map;

        @Override
        public Integer call() {
            ark.maps().apply(map);
            System.out.println("✓ applied " + map);
            return 0;
        }
    }

    @Command(name = "customize", description = "Give a map its own config (copies default).")
    static final class Customize implements Callable<Integer> {
        @ParentCommand ArkCommand ark;
        @Parameters(index = "0", paramLabel = "<map>") String map;

        @Override
        public Integer call() {
            System.out.println(ark.maps().customize(map)
                    ? "✓ " + map + " now has its own config" : map + " is already custom");
            return 0;
        }
    }

    @Command(name = "uncustomize", description = "Delete a map's custom config (revert to default).")
    static final class Uncustomize implements Callable<Integer> {
        @ParentCommand ArkCommand ark;
        @Parameters(index = "0", paramLabel = "<map>") String map;

        @Override
        public Integer call() {
            System.out.println(ark.maps().uncustomize(map)
                    ? "✓ " + map + " reverted to default" : map + " was not custom");
            return 0;
        }
    }

    @Command(name = "mods", description = "Show the catalog, or a map's effective mods.")
    static final class ListMods implements Callable<Integer> {
        @ParentCommand ArkCommand ark;
        @Parameters(index = "0", arity = "0..1", paramLabel = "<map>") String map;

        @Override
        public Integer call() {
            List<Mod> list = (map == null) ? ark.mods().catalog() : ark.mods().modsOf(map);
            for (Mod m : list) {
                System.out.printf("%-12s %s%n", m.id(), m.name());
            }
            return 0;
        }
    }

    @Command(name = "mod-add", description = "Add a mod to the registry by Workshop id (name auto-fetched).")
    static final class AddMod implements Callable<Integer> {
        @ParentCommand ArkCommand ark;
        @Parameters(index = "0", paramLabel = "<id>") String id;

        @Override
        public Integer call() {
            Mod m = ark.mods().addMod(id);
            System.out.println("✓ " + m.id() + "  " + m.name());
            return 0;
        }
    }

    @Command(name = "mod-sync", description = "Fetch any missing mod names from Steam.")
    static final class SyncMods implements Callable<Integer> {
        @ParentCommand ArkCommand ark;

        @Override
        public Integer call() {
            int n = ark.mods().syncRegistry();
            System.out.println(n == 0 ? "registry up to date" : "✓ added " + n + " name(s)");
            return 0;
        }
    }

    @Command(name = "set-mods", description = "Set a map's mods (comma-separated ids).")
    static final class SetMods implements Callable<Integer> {
        @ParentCommand ArkCommand ark;
        @Parameters(index = "0", paramLabel = "<map>") String map;
        @Parameters(index = "1", paramLabel = "<id,id,...>") String ids;

        @Override
        public Integer call() {
            ark.mods().setMapMods(map,
                    Arrays.stream(ids.split(",")).map(String::strip).filter(s -> !s.isBlank()).toList());
            System.out.println("✓ set " + map + " mods");
            return 0;
        }
    }
}
