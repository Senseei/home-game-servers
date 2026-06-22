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

import static org.jline.keymap.KeyMap.key;

/**
 * Inline arrow-key single-select — like {@code gum choose}: the list is drawn in
 * place in the normal terminal buffer (scrollback kept, no full-screen takeover),
 * ↑/↓ move a highlight, enter picks, q/esc cancels.
 */
final class Chooser {

    private Chooser() {
    }

    /** @return the chosen index, or -1 if cancelled. */
    static int choose(Terminal terminal, List<String> options) {
        PrintWriter writer = terminal.writer();

        KeyMap<String> keys = new KeyMap<>();
        keys.bind("up", key(terminal, Capability.key_up), "\033[A", "k");
        keys.bind("down", key(terminal, Capability.key_down), "\033[B", "j");
        keys.bind("select", "\r", "\n");
        keys.bind("cancel", "\033", "q");
        BindingReader reader = new BindingReader(terminal.reader());

        Attributes previous = terminal.getAttributes();
        terminal.enterRawMode();
        try {
            int selected = 0;
            render(writer, options, selected, false);
            while (true) {
                String op = reader.readBinding(keys);
                if (op == null) {
                    continue;
                }
                switch (op) {
                    case "up" -> selected = (selected - 1 + options.size()) % options.size();
                    case "down" -> selected = (selected + 1) % options.size();
                    case "select" -> { return selected; }
                    case "cancel" -> { return -1; }
                    default -> { }
                }
                render(writer, options, selected, true);
            }
        } finally {
            terminal.setAttributes(previous);
        }
    }

    private static void render(PrintWriter writer, List<String> options, int selected, boolean redraw) {
        if (redraw) {
            writer.printf("\033[%dA", options.size());   // back up to the first option line
        }
        for (int i = 0; i < options.size(); i++) {
            writer.print("\r\033[K");                     // carriage return + clear line
            if (i == selected) {
                writer.print(new AttributedStringBuilder()
                        .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold())
                        .append("❯ ").append(options.get(i)).toAnsi());
            } else {
                writer.print("  " + options.get(i));
            }
            writer.print("\r\n");
        }
        writer.flush();
    }
}
