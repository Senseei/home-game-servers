package com.senseei.launcher.ark.port;

import com.senseei.launcher.ark.domain.MapConfig;

import java.nio.file.Path;
import java.util.List;

/** Repository for the {@link MapConfig} aggregate. */
public interface MapConfigRepository {

    /**
     * The effective config for a target: {@code "default"} → the default config;
     * a map name → its own config if customized, else default's (with custom=false).
     */
    MapConfig load(String target);

    /** Whether the map has its own config (is customized). */
    boolean exists(String map);

    List<String> customMaps();

    /** Persists a config to its own dir ({@code default/} or {@code maps/<name>/}). */
    void save(MapConfig config);

    /** Removes a map's custom config (revert to inheriting default). */
    void delete(String map);

    /** The on-disk config files to edit for a target (custom dir if customized, else default). */
    List<Path> configFiles(String target);
}
