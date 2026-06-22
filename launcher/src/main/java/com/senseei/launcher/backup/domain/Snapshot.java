package com.senseei.launcher.backup.domain;

/** A backup archive: which game it belongs to and its file name. */
public record Snapshot(String game, String fileName) {
}
