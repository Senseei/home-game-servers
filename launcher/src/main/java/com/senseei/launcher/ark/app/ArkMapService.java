package com.senseei.launcher.ark.app;

import com.senseei.launcher.shared.port.EnvStore;
import com.senseei.launcher.ark.port.LiveConfig;
import com.senseei.launcher.ark.port.MapConfigRepository;
import com.senseei.launcher.ark.domain.IniMerge;
import com.senseei.launcher.ark.domain.MapConfig;
import com.senseei.launcher.ark.domain.OfficialMaps;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Use-cases for ARK maps — thin orchestration: load the {@link MapConfig}
 * aggregate, apply it via the {@link IniMerge} domain service, persist.
 */
public final class ArkMapService {

    private static final String SERVER_SETTINGS = "[ServerSettings]";

    private final MapConfigRepository maps;
    private final LiveConfig live;
    private final EnvStore env;

    public ArkMapService(MapConfigRepository maps, LiveConfig live, EnvStore env) {
        this.maps = maps;
        this.live = live;
        this.env = env;
    }

    public List<MapChoice> selectable() {
        Set<String> names = new TreeSet<>(OfficialMaps.ALL);
        names.addAll(maps.customMaps());
        return names.stream().map(n -> new MapChoice(n, maps.exists(n))).toList();
    }

    public void apply(String map) {
        MapConfig cfg = maps.load(map);
        env.set("ARK_MAP", map);
        env.set("ARK_MODS", String.join(",", cfg.modIds()));
        String merged = IniMerge.merge(live.gameUserSettings(), cfg.gameUserSettings(), SERVER_SETTINGS);
        live.write(merged, cfg.gameIni());
    }

    public boolean customize(String map) {
        if (maps.exists(map)) {
            return false;
        }
        maps.save(maps.load("default").asMap(map));
        return true;
    }

    public boolean uncustomize(String map) {
        if (!maps.exists(map)) {
            return false;
        }
        maps.delete(map);
        return true;
    }
}
