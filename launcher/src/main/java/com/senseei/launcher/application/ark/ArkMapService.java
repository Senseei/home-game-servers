package com.senseei.launcher.application.ark;

import com.senseei.launcher.application.port.ConfigStore;
import com.senseei.launcher.application.port.EnvStore;
import com.senseei.launcher.domain.ark.IniMerge;
import com.senseei.launcher.domain.ark.OfficialMaps;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Use-cases for ARK maps. A map inherits {@code default/} until customized; this
 * service holds the inherit-or-custom rule and drives the {@link IniMerge}.
 */
public final class ArkMapService {

    private static final String SERVER_SETTINGS = "[ServerSettings]";

    private final ConfigStore config;
    private final EnvStore env;

    public ArkMapService(ConfigStore config, EnvStore env) {
        this.config = config;
        this.env = env;
    }

    /** Official maps plus any customized ones, de-duped + sorted, each marked. */
    public List<MapChoice> selectable() {
        Set<String> names = new TreeSet<>(OfficialMaps.ALL);
        names.addAll(config.customMaps());
        List<MapChoice> out = new ArrayList<>();
        for (String n : names) {
            out.add(new MapChoice(n, config.mapExists(n)));
        }
        return out;
    }

    /** Loads a map's config + mods into the live server (its custom copy if any, else default). */
    public void apply(String map) {
        String source = config.mapExists(map) ? map : "default";

        env.set("ARK_MAP", map);
        env.set("ARK_MODS", String.join(",", config.readModIds(source)));

        String merged = IniMerge.merge(
                config.readLiveGameUserSettings(),
                config.readGameUserSettings(source),
                SERVER_SETTINGS);
        config.writeLiveGameUserSettings(merged);
        config.writeLiveGameIni(config.readGameIni(source));
    }

    /** Gives a map its own config (a copy of default). Returns false if already custom. */
    public boolean customize(String map) {
        if (config.mapExists(map)) {
            return false;
        }
        config.copyDefaultTo(map);
        return true;
    }

    /** Deletes a map's custom config so it inherits default again. False if not custom. */
    public boolean uncustomize(String map) {
        if (!config.mapExists(map)) {
            return false;
        }
        config.deleteMap(map);
        return true;
    }
}
