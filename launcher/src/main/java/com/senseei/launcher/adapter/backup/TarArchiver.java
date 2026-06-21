package com.senseei.launcher.adapter.backup;

import com.senseei.launcher.application.port.Archiver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Archiver that shells out to {@code tar} ({@code -h} follows the data symlinks to the HDD). */
public final class TarArchiver implements Archiver {

    @Override
    public void create(Path sourceDir, Path destArchive) {
        int code = run("tar", "-czhf", destArchive.toString(),
                "-C", sourceDir.getParent().toString(), sourceDir.getFileName().toString());
        // 1 = "some files changed while reading" (benign — e.g. an autosave mid-tar); >=2 is fatal.
        if (code > 1) {
            throw new RuntimeException("tar failed (exit " + code + ")");
        }
    }

    @Override
    public void extract(Path archive, Path destDir) {
        if (run("tar", "-xzf", archive.toString(), "-C", destDir.toString()) != 0) {
            throw new RuntimeException("tar extract failed");
        }
    }

    private static int run(String... cmd) {
        try {
            return new ProcessBuilder(cmd).inheritIO().start().waitFor();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
    }
}
