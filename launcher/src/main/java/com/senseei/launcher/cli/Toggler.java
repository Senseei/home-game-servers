package com.senseei.launcher.cli;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp.Capability;

import java.io.PrintWriter;
import java.util.List;
import java.util.function.IntPredicate;

import static org.jline.keymap.KeyMap.key;

/**
 * Inline multi-toggle checklist — ↑/↓ move, space flips the highlighted item's
 * {@code [x]}/{@code [ ]} (performing the change live via the callback), enter or
 * esc/q finishes. Drawn in place like {@link Chooser}; no full-screen takeover.
 */
final class Toggler {

    private Toggler() {
    }

    /**
     * @param state    current on/off per item; updated in place as the user toggles
     * @param onToggle flips item {@code i} (performs the side effect) and returns its new state
     */
    static void run(Terminal terminal, List<String> labels, boolean[] state, IntPredicate onToggle) {
        PrintWriter writer = terminal.writer();

        KeyMap<String> keys = new KeyMap<>();
        keys.bind("up", key(terminal, Capability.key_up), "\033[A", "k");
        keys.bind("down", key(terminal, Capability.key_down), "\033[B", "j");
        keys.bind("toggle", " ");
        keys.bind("done", "\r", "\n", "\033", "q");
        BindingReader reader = new BindingReader(terminal.reader());

        Attributes previous = terminal.getAttributes();
        terminal.enterRawMode();
        try {
            int selected = 0;
            render(writer, labels, state, selected, false);
            while (true) {
                String op = reader.readBinding(keys);
                if (op == null) {
                    continue;
                }
                switch (op) {
                    case "up" -> selected = (selected - 1 + labels.size()) % labels.size();
                    case "down" -> selected = (selected + 1) % labels.size();
                    case "toggle" -> state[selected] = onToggle.test(selected);
                    case "done" -> { return; }
                    default -> { }
                }
                render(writer, labels, state, selected, true);
            }
        } finally {
            terminal.setAttributes(previous);
        }
    }

    private static void render(PrintWriter writer, List<String> labels, boolean[] state, int selected, boolean redraw) {
        if (redraw) {
            writer.printf("\033[%dA", labels.size());
        }
        for (int i = 0; i < labels.size(); i++) {
            writer.print("\r\033[K");
            String box = state[i] ? "[x] " : "[ ] ";
            if (i == selected) {
                writer.print(new AttributedStringBuilder()
                        .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold())
                        .append("❯ ").append(box).append(labels.get(i)).toAnsi());
            } else {
                writer.print("  " + box + labels.get(i));
            }
            writer.print("\r\n");
        }
        writer.flush();
    }
}
