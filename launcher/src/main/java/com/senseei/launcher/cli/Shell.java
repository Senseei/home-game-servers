package com.senseei.launcher.cli;

import com.senseei.launcher.ark.app.ArkMapService;
import com.senseei.launcher.ark.app.MapChoice;
import com.senseei.launcher.ark.app.ModCatalogService;
import com.senseei.launcher.backup.app.BackupService;
import com.senseei.launcher.backup.domain.Snapshot;
import com.senseei.launcher.lifecycle.app.ServerLifecycle;
import com.senseei.launcher.lifecycle.app.ServerStatus;
import com.senseei.launcher.lifecycle.domain.RunState;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Interactive menu — you arrow through options and hit enter; nothing to type.
 * Inline in the terminal (scrollback kept), organized by the three feature
 * slices. Pure presentation: it only calls use-cases and prints their results.
 */
public final class Shell {

    private static final String BACK = "← back";

    private final ServerLifecycle lifecycle;
    private final ArkMapService arkMaps;
    private final ModCatalogService mods;
    private final BackupService backups;

    private Terminal terminal;
    private PrintWriter out;

    public Shell(ServerLifecycle lifecycle, ArkMapService arkMaps, ModCatalogService mods, BackupService backups) {
        this.lifecycle = lifecycle;
        this.arkMaps = arkMaps;
        this.mods = mods;
        this.backups = backups;
    }

    public void run() throws IOException {
        try (Terminal t = TerminalBuilder.builder().build()) {
            terminal = t;
            out = t.writer();
            out.println("↑/↓ move · enter select · q or esc to go back");
            while (true) {
                int c = menu("home-game-servers", List.of("Servers", "ARK", "Backups", "Quit"));
                if (c < 0 || c == 3) {
                    out.println("bye 👋");
                    out.flush();
                    return;
                }
                switch (c) {
                    case 0 -> servers();
                    case 1 -> ark();
                    case 2 -> backupMenu();
                    default -> { }
                }
            }
        }
    }

    // ── Servers ──────────────────────────────────────────────────────────────
    private void servers() {
        while (true) {
            List<ServerStatus> statuses = lifecycle.status();
            List<String> items = new ArrayList<>();
            for (ServerStatus s : statuses) {
                items.add(String.format("%-12s %s", s.game(), s.state() == RunState.RUNNING ? "● running" : "○ stopped"));
            }
            items.add(BACK);
            int c = menu("Servers", items);
            if (c < 0 || c == statuses.size()) {
                return;
            }
            String game = statuses.get(c).game();
            int a = menu(game, List.of("Start", "Stop", "Restart", BACK));
            if (a < 0 || a == 3) {
                continue;
            }
            result(() -> {
                switch (a) {
                    case 0 -> lifecycle.start(game);
                    case 1 -> lifecycle.stop(game);
                    case 2 -> lifecycle.restart(game);
                    default -> { }
                }
                return "✓ " + game + " — " + List.of("start", "stop", "restart").get(a) + " issued";
            });
        }
    }

    // ── ARK ──────────────────────────────────────────────────────────────────
    private void ark() {
        while (true) {
            int c = menu("ARK", List.of("Switch map", "Customize a map", "View a map's mods", BACK));
            if (c < 0 || c == 3) {
                return;
            }
            switch (c) {
                case 0 -> pickMap("Switch map", map ->
                        result(() -> { arkMaps.apply(map); return "✓ applied " + map + " — restart ARK to take effect"; }));
                case 1 -> pickMap("Customize a map", map ->
                        result(() -> arkMaps.customize(map) ? "✓ " + map + " now has a custom config" : map + " is already custom"));
                case 2 -> pickMap("View a map's mods", this::showMods);
                default -> { }
            }
        }
    }

    private void pickMap(String heading, Consumer<String> onPick) {
        List<MapChoice> maps = arkMaps.selectable();
        List<String> items = new ArrayList<>();
        for (MapChoice m : maps) {
            items.add(String.format("%-16s %s", m.name(), m.custom() ? "● custom" : "○ default"));
        }
        items.add(BACK);
        int c = menu(heading, items);
        if (c >= 0 && c != maps.size()) {
            onPick.accept(maps.get(c).name());
        }
    }

    private void showMods(String map) {
        out.println(map + " mods:");
        var list = mods.modsOf(map);
        if (list.isEmpty()) {
            out.println("  (none)");
        } else {
            list.forEach(m -> out.println("  " + m.id() + "  " + m.name()));
        }
        out.flush();
    }

    // ── Backups ──────────────────────────────────────────────────────────────
    private void backupMenu() {
        List<String> games = List.of("minecraft", "palworld", "ark-se", BACK);
        int g = menu("Backups — which game?", games);
        if (g < 0 || g == 3) {
            return;
        }
        String game = games.get(g);
        int a = menu(game, List.of("Back up now", "Restore latest", BACK));
        if (a < 0 || a == 2) {
            return;
        }
        result(() -> {
            if (a == 0) {
                Snapshot s = backups.backup(game);
                return "✓ backed up → " + s.fileName();
            }
            backups.restore(game, "latest");
            return "✓ restored from latest";
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private int menu(String heading, List<String> options) {
        out.println();
        out.println(heading);
        out.flush();
        int selected = Chooser.choose(terminal, options);
        eraseLines(options.size() + 2);   // wipe options + heading + blank so the view refreshes in place
        return selected;
    }

    /** Move the cursor up {@code n} lines and clear to the end of the screen. */
    private void eraseLines(int n) {
        out.print("\033[" + n + "A\033[J");
        out.flush();
    }

    private void result(Callable<String> action) {
        try {
            out.println(action.call());
        } catch (Exception e) {
            out.println("✗ " + e.getMessage());
        }
        out.flush();
    }
}
