package com.senseei.launcher.ark.domain;

import java.util.List;

/**
 * Aggregate: one map's config — its GameUserSettings + Game.ini text and its mod
 * list — loaded and saved as a unit. {@code custom} says whether it's the map's
 * own config or inherited from default.
 */
public final class MapConfig {

    private final String name;
    private final boolean custom;
    private final String gameUserSettings;
    private final String gameIni;
    private List<String> modIds;

    public MapConfig(String name, boolean custom, String gameUserSettings, String gameIni, List<String> modIds) {
        this.name = name;
        this.custom = custom;
        this.gameUserSettings = gameUserSettings;
        this.gameIni = gameIni;
        this.modIds = List.copyOf(modIds);
    }

    public String name() {
        return name;
    }

    public boolean isCustom() {
        return custom;
    }

    public String gameUserSettings() {
        return gameUserSettings;
    }

    public String gameIni() {
        return gameIni;
    }

    public List<String> modIds() {
        return modIds;
    }

    /** Replaces this map's mod list. */
    public void setMods(List<String> ids) {
        this.modIds = List.copyOf(ids);
    }

    /** This config owned by another map — used when customizing (copy default's data). */
    public MapConfig asMap(String newName) {
        return new MapConfig(newName, true, gameUserSettings, gameIni, modIds);
    }
}
