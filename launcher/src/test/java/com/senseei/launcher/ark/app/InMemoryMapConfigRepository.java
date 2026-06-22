package com.senseei.launcher.ark.app;

import com.senseei.launcher.ark.port.MapConfigRepository;
import com.senseei.launcher.ark.domain.MapConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory MapConfigRepository for use-case tests — no filesystem. */
final class InMemoryMapConfigRepository implements MapConfigRepository {

    private final Map<String, MapConfig> stored = new HashMap<>();   // name -> config (incl. "default")
    private final Set<String> custom = new HashSet<>();

    InMemoryMapConfigRepository(MapConfig defaultConfig) {
        stored.put("default", defaultConfig);
    }

    @Override
    public MapConfig load(String target) {
        if (target.equals("default") || custom.contains(target)) {
            return stored.get(target);
        }
        MapConfig def = stored.get("default");
        return new MapConfig(target, false, def.gameUserSettings(), def.gameIni(), def.modIds());
    }

    @Override
    public boolean exists(String map) {
        return custom.contains(map);
    }

    @Override
    public List<String> customMaps() {
        List<String> l = new ArrayList<>(custom);
        Collections.sort(l);
        return l;
    }

    @Override
    public void save(MapConfig config) {
        stored.put(config.name(), config);
        if (!config.name().equals("default")) {
            custom.add(config.name());
        }
    }

    @Override
    public void delete(String map) {
        custom.remove(map);
        stored.remove(map);
    }
}
