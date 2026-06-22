package com.senseei.launcher.cli;

import com.senseei.launcher.ark.app.ArkMapService;
import com.senseei.launcher.ark.app.MapChoice;
import com.senseei.launcher.ark.app.ModCatalogService;
import com.senseei.launcher.backup.app.BackupService;
import com.senseei.launcher.backup.domain.Snapshot;
import com.senseei.launcher.lifecycle.app.ServerLifecycle;
import com.senseei.launcher.lifecycle.app.ServerStatus;
import com.senseei.launcher.lifecycle.domain.RunState;
import org.jline.terminal.Attributes;
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
 * Inline in the terminal (scrollback kept), organized by the three feature slices.
 * Menus and one-off output erase themselves so the view never accumulates. Pure
 * presentation: it only calls use-cases and prints their results.
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
            int c = menu("ARK", List.of("Switch map", "Edit config", "Customize a map",
                    "Uncustomize a map", "View a map's mods", BACK));
            if (c < 0 || c == 5) {
                return;
            }
            switch (c) {
                case 0 -> pickMap("Switch map", map ->
                        result(() -> { arkMaps.apply(map); return "✓ applied " + map + " — restart ARK to take effect"; }));
                case 1 -> pickName("Edit config", arkMaps.editableTargets(), this::edit);
                case 2 -> pickMap("Customize a map", map ->
                        result(() -> arkMaps.customize(map) ? "✓ " + map + " now has a custom config" : map + " is already custom"));
                case 3 -> pickName("Uncustomize a map", arkMaps.customMaps(), map ->
                        result(() -> arkMaps.uncustomize(map) ? "✓ " + map + " reverted to default" : map + " was not custom"));
                case 4 -> pickMap("View a map's mods", this::showMods);
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

    private void pickName(String heading, List<String> names, Consumer<String> onPick) {
        if (names.isEmpty()) {
            flash(List.of("(nothing here yet)"));
            return;
        }
        List<String> items = new ArrayList<>(names);
        items.add(BACK);
        int c = menu(heading, items);
        if (c >= 0 && c != names.size()) {
            onPick.accept(names.get(c));
        }
    }

    private void edit(String target) {
        String editor = System.getenv().getOrDefault("EDITOR", "nano");
        List<String> command = new ArrayList<>();
        command.add(editor);
        arkMaps.configFiles(target).forEach(f -> command.add(f.toString()));
        try {
            new ProcessBuilder(command).inheritIO().start().waitFor();
        } catch (IOException e) {
            flash(List.of("✗ couldn't launch " + editor + ": " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void showMods(String map) {
        List<String> lines = new ArrayList<>();
        lines.add(map + " mods:");
        var list = mods.modsOf(map);
        if (list.isEmpty()) {
            lines.add("  (none)");
        } else {
            list.forEach(m -> lines.add("  " + m.id() + "  " + m.name()));
        }
        flash(lines);
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

    /** Show one-off output, then wait for a key and erase it — so nothing piles up. */
    private void flash(List<String> lines) {
        lines.forEach(out::println);
        out.println("· press a key ·");
        out.flush();
        readKey();
        eraseLines(lines.size() + 1);
    }

    private void result(Callable<String> action) {
        String message;
        try {
            message = action.call();
        } catch (Exception e) {
            message = "✗ " + e.getMessage();
        }
        flash(List.of(message));
    }

    private void readKey() {
        Attributes previous = terminal.getAttributes();
        terminal.enterRawMode();
        try {
            terminal.reader().read();
        } catch (IOException ignored) {
            // nothing to do
        } finally {
            terminal.setAttributes(previous);
        }
    }

    /** Move the cursor up {@code n} lines and clear to the end of the screen. */
    private void eraseLines(int n) {
        out.print("\033[" + n + "A\033[J");
        out.flush();
    }
}
