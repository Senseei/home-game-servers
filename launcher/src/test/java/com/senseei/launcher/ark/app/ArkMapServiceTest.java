package com.senseei.launcher.ark.app;

import com.senseei.launcher.shared.port.EnvStore;
import com.senseei.launcher.ark.port.LiveConfig;
import com.senseei.launcher.ark.domain.MapConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArkMapServiceTest {

    static final class FakeEnv implements EnvStore {
        final Map<String, String> values = new HashMap<>();
        @Override public Optional<String> get(String k) { return Optional.ofNullable(values.get(k)); }
        @Override public void set(String k, String v) { values.put(k, v); }
    }

    static final class FakeLive implements LiveConfig {
        String gus = "[ServerSettings]\nServerPassword=secret\nXPMultiplier=1.0\n";
        String gameIni = "";
        @Override public String gameUserSettings() { return gus; }
        @Override public void write(String g, String gi) { gus = g; gameIni = gi; }
    }

    private static MapConfig defaultCfg(String gus, String gameIni, List<String> mods) {
        return new MapConfig("default", false, gus, gameIni, mods);
    }

    @Test
    void applyMergesDefaultAndPreservesManagedKeys() {
        var repo = new InMemoryMapConfigRepository(
                defaultCfg("[ServerSettings]\nXPMultiplier=2.0\n", "[/script]\n", List.of("111", "222")));
        FakeLive live = new FakeLive();
        FakeEnv env = new FakeEnv();

        new ArkMapService(repo, live, env).apply("Ragnarok");

        assertEquals("Ragnarok", env.values.get("ARK_MAP"));
        assertEquals("111,222", env.values.get("ARK_MODS"));
        assertTrue(live.gus.contains("ServerPassword=secret"));
        assertTrue(live.gus.contains("XPMultiplier=2.0"));
        assertFalse(live.gus.contains("XPMultiplier=1.0"));
    }

    @Test
    void applyPrefersCustomConfig() {
        var repo = new InMemoryMapConfigRepository(
                defaultCfg("[ServerSettings]\nXPMultiplier=2.0\n", "", List.of("111")));
        repo.save(new MapConfig("TheCenter", true, "[ServerSettings]\nXPMultiplier=9.0\n", "", List.of("999")));
        FakeLive live = new FakeLive();
        FakeEnv env = new FakeEnv();

        new ArkMapService(repo, live, env).apply("TheCenter");

        assertEquals("999", env.values.get("ARK_MODS"));
        assertTrue(live.gus.contains("XPMultiplier=9.0"));
    }

    @Test
    void customizeCopiesDefaultThenUncustomize() {
        var repo = new InMemoryMapConfigRepository(defaultCfg("[ServerSettings]\n", "", List.of("111")));
        ArkMapService svc = new ArkMapService(repo, new FakeLive(), new FakeEnv());

        assertTrue(svc.customize("Fjordur"));
        assertTrue(repo.exists("Fjordur"));
        assertEquals(List.of("111"), repo.load("Fjordur").modIds());   // inherited default's mods
        assertFalse(svc.customize("Fjordur"));                         // already custom
        assertTrue(svc.uncustomize("Fjordur"));
        assertFalse(repo.exists("Fjordur"));
    }

    @Test
    void selectableMarksCustomVsDefault() {
        var repo = new InMemoryMapConfigRepository(defaultCfg("", "", List.of()));
        repo.save(new MapConfig("TheCenter", true, "", "", List.of()));
        List<MapChoice> sel = new ArkMapService(repo, new FakeLive(), new FakeEnv()).selectable();
        assertTrue(sel.stream().anyMatch(c -> c.name().equals("TheCenter") && c.custom()));
        assertTrue(sel.stream().anyMatch(c -> c.name().equals("Ragnarok") && !c.custom()));
    }
}
