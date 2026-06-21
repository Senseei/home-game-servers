package com.senseei.launcher.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Locates the repo root: the nearest ancestor of the cwd holding games/ and scripts/. */
public final class RepoRoot {

    private RepoRoot() {
    }

    public static Path find() {
        for (Path p = Paths.get("").toAbsolutePath(); p != null; p = p.getParent()) {
            if (isRoot(p)) {
                return p;
            }
        }
        throw new IllegalStateException("repo root not found (need a dir with games/ and scripts/)");
    }

    private static boolean isRoot(Path dir) {
        return Files.isDirectory(dir.resolve("games"))
                && Files.isDirectory(dir.resolve("scripts"));
    }
}
