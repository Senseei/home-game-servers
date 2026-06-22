package com.senseei.launcher.tui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.ListSelectDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.senseei.launcher.ark.app.ArkMapService;
import com.senseei.launcher.ark.app.MapChoice;
import com.senseei.launcher.ark.app.ModCatalogService;
import com.senseei.launcher.backup.app.BackupService;
import com.senseei.launcher.backup.domain.Snapshot;
import com.senseei.launcher.lifecycle.app.ServerLifecycle;
import com.senseei.launcher.lifecycle.app.ServerStatus;
import com.senseei.launcher.lifecycle.domain.RunState;

import java.io.IOException;
import java.util.List;

/**
 * Interactive terminal UI — a navigable front end over the use-cases, organized
 * by feature (Servers / ARK / Backups). Opened by running `ctl` with no args;
 * `ctl <command>` stays the scriptable CLI. Pure presentation: it only calls
 * use-cases and renders their data.
 */
public final class Tui {

    private final ServerLifecycle lifecycle;
    private final ArkMapService arkMaps;
    private final ModCatalogService mods;
    private final BackupService backups;

    private WindowBasedTextGUI gui;

    public Tui(ServerLifecycle lifecycle, ArkMapService arkMaps, ModCatalogService mods, BackupService backups) {
        this.lifecycle = lifecycle;
        this.arkMaps = arkMaps;
        this.mods = mods;
        this.backups = backups;
    }

    public void run() throws IOException {
        Screen screen = new DefaultTerminalFactory().createScreen();
        screen.startScreen();
        gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLACK));
        try {
            String choice;
            while ((choice = select("home-game-servers", "↑/↓ move · enter select · esc quit",
                    "Servers", "ARK", "Backups")) != null) {
                switch (choice) {
                    case "Servers" -> servers();
                    case "ARK" -> ark();
                    case "Backups" -> backupsSection();
                    default -> { }
                }
            }
        } finally {
            screen.stopScreen();
        }
    }

    // ── Servers ──────────────────────────────────────────────────────────────
    private void servers() {
        String picked;
        while ((picked = select("Servers", "select a server · esc back", serverLabels())) != null) {
            String game = firstWord(picked);
            String action = select(game, "action", "Start", "Stop", "Restart");
            if (action == null) {
                continue;
            }
            run(game, () -> {
                switch (action) {
                    case "Start" -> lifecycle.start(game);
                    case "Stop" -> lifecycle.stop(game);
                    case "Restart" -> lifecycle.restart(game);
                    default -> { }
                }
                return "✓ " + action.toLowerCase() + " issued";
            });
        }
    }

    private String[] serverLabels() {
        return lifecycle.status().stream()
                .map(s -> String.format("%-12s %s", s.game(), running(s) ? "● running" : "○ stopped"))
                .toArray(String[]::new);
    }

    private static boolean running(ServerStatus s) {
        return s.state() == RunState.RUNNING;
    }

    // ── ARK ──────────────────────────────────────────────────────────────────
    private void ark() {
        String choice;
        while ((choice = select("ARK", "esc back", "Switch map", "Customize a map", "View a map's mods")) != null) {
            switch (choice) {
                case "Switch map" -> pickMap("Switch map", "loads its config + mods · esc back", map ->
                        run("ARK", () -> { arkMaps.apply(map); return "✓ applied " + map + " — restart ARK to take effect"; }));
                case "Customize a map" -> pickMap("Customize", "give a map its own config · esc back", map ->
                        info("ARK", arkMaps.customize(map) ? "✓ " + map + " now has a custom config" : map + " is already custom"));
                case "View a map's mods" -> pickMap("View mods", "which map? · esc back", map -> {
                    String list = String.join("\n", mods.modsOf(map).stream().map(m -> m.id() + "  " + m.name()).toList());
                    info(map + " mods", list.isBlank() ? "(none)" : list);
                });
                default -> { }
            }
        }
    }

    private void pickMap(String title, String desc, java.util.function.Consumer<String> onPick) {
        String[] items = arkMaps.selectable().stream()
                .map(m -> String.format("%-16s %s", m.name(), m.custom() ? "● custom" : "○ default"))
                .toArray(String[]::new);
        String picked = select(title, desc, items);
        if (picked != null) {
            onPick.accept(firstWord(picked));
        }
    }

    // ── Backups ──────────────────────────────────────────────────────────────
    private void backupsSection() {
        String game = select("Backups", "which game? · esc back", "minecraft", "palworld", "ark-se");
        if (game == null) {
            return;
        }
        String action = select(game, "esc back", "Back up now", "Restore latest");
        if (action == null) {
            return;
        }
        run(game, () -> {
            if (action.equals("Back up now")) {
                Snapshot s = backups.backup(game);
                return "✓ backed up → " + s.fileName();
            }
            backups.restore(game, "latest");
            return "✓ restored from latest";
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private String select(String title, String description, String... items) {
        if (items.length == 0) {
            info(title, "(nothing here)");
            return null;
        }
        return ListSelectDialog.showDialog(gui, title, description, items);
    }

    private void info(String title, String text) {
        MessageDialog.showMessageDialog(gui, title, text);
    }

    /** Run an action that yields a result message, reporting failures instead of crashing the UI. */
    private void run(String title, java.util.concurrent.Callable<String> action) {
        try {
            info(title, action.call());
        } catch (Exception e) {
            info(title, "✗ " + e.getMessage());
        }
    }

    private static String firstWord(String s) {
        return s.trim().split("\\s+")[0];
    }
}
