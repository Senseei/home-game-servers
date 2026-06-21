package com.senseei.launcher.application.ark;

import com.senseei.launcher.application.port.ConfigStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory ConfigStore for use-case tests — no filesystem. */
final class InMemoryConfigStore implements ConfigStore {

    final Set<String> maps = new HashSet<>();
    final Map<String, String> gus = new HashMap<>();
    final Map<String, String> gameIni = new HashMap<>();
    final Map<String, List<String>> mods = new HashMap<>();
    String liveGus = "[ServerSettings]\nServerPassword=secret\nXPMultiplier=1.0\n";
    String liveGameIni = "";

    @Override public boolean mapExists(String m) { return maps.contains(m); }

    @Override public List<String> customMaps() {
        List<String> l = new ArrayList<>(maps);
        Collections.sort(l);
        return l;
    }

    @Override public void copyDefaultTo(String m) {
        maps.add(m);
        gus.put(m, gus.getOrDefault("default", ""));
        gameIni.put(m, gameIni.getOrDefault("default", ""));
        mods.put(m, mods.getOrDefault("default", List.of()));
    }

    @Override public void deleteMap(String m) { maps.remove(m); }
    @Override public String readGameUserSettings(String s) { return gus.getOrDefault(s, ""); }
    @Override public String readGameIni(String s) { return gameIni.getOrDefault(s, ""); }
    @Override public List<String> readModIds(String s) { return mods.getOrDefault(s, List.of()); }
    @Override public void writeModIds(String s, List<String> ids) { mods.put(s, ids); }
    @Override public String readLiveGameUserSettings() { return liveGus; }
    @Override public void writeLiveGameUserSettings(String c) { liveGus = c; }
    @Override public void writeLiveGameIni(String c) { liveGameIni = c; }
}
