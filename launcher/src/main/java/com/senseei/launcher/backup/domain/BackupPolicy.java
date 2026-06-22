package com.senseei.launcher.backup.domain;

import java.util.List;

/** Retention policy: keep the newest {@code keep}, prune the rest. Pure — no I/O. */
public final class BackupPolicy {

    private BackupPolicy() {
    }

    /**
     * Given items ordered newest-first, returns the ones beyond {@code keep}
     * (the ones to delete). {@code keep <= 0} keeps everything (no pruning).
     */
    public static <T> List<T> surplus(List<T> newestFirst, int keep) {
        if (keep <= 0 || newestFirst.size() <= keep) {
            return List.of();
        }
        return List.copyOf(newestFirst.subList(keep, newestFirst.size()));
    }
}
