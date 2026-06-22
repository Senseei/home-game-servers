package com.senseei.launcher.cli;

import org.jline.console.SystemRegistry;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Parser;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

/**
 * Interactive CLI shell — a JLine REPL over the same Picocli commands. {@code ctl}
 * with no args drops you at a {@code ctl>} prompt: tab-completion, history (↑/↓),
 * and inline output (no full-screen takeover). Same commands as the one-shot CLI;
 * this is just an interactive front end to them.
 */
public final class Shell {

    private Shell() {
    }

    public static void run(CommandLine cmd) throws IOException {
        Supplier<Path> workDir = () -> Paths.get(System.getProperty("user.dir"));
        PicocliCommands commands = new PicocliCommands(cmd);
        Parser parser = new DefaultParser();
        try (Terminal terminal = TerminalBuilder.builder().build()) {
            SystemRegistry registry = new SystemRegistryImpl(parser, terminal, workDir, null);
            registry.setCommandRegistries(commands);
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(registry.completer())
                    .parser(parser)
                    .variable(LineReader.LIST_MAX, 50)
                    .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".jctl_history"))
                    .build();

            terminal.writer().println("home-game-servers · interactive · tab completes · ↑ history · Ctrl-D quits");
            terminal.writer().flush();

            while (true) {
                try {
                    registry.cleanUp();
                    String line = reader.readLine("ctl> ");
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    String trimmed = line.strip();
                    if (trimmed.equals("exit") || trimmed.equals("quit")) {
                        return;
                    }
                    registry.execute(line);
                } catch (UserInterruptException ignored) {
                    // Ctrl-C — abandon the line, keep the shell open
                } catch (EndOfFileException e) {
                    return; // Ctrl-D
                } catch (Exception e) {
                    terminal.writer().println("✗ " + e.getMessage());
                    terminal.writer().flush();
                }
            }
        }
    }
}
