package com.senseei.launcher.application.ark;

import com.senseei.launcher.application.port.EnvStore;
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

    @Test
    void applyUsesDefaultAndPreservesManagedKeys() {
        InMemoryConfigStore cfg = new InMemoryConfigStore();
        cfg.gus.put("default", "[ServerSettings]\nXPMultiplier=2.0\n");
        cfg.gameIni.put("default", "[/script/shootergame.shootergamemode]\n");
        cfg.mods.put("default", List.of("111", "222"));
        FakeEnv env = new FakeEnv();

        new ArkMapService(cfg, env).apply("Ragnarok");

        assertEquals("Ragnarok", env.values.get("ARK_MAP"));
        assertEquals("111,222", env.values.get("ARK_MODS"));
        assertTrue(cfg.liveGus.contains("ServerPassword=secret"));
        assertTrue(cfg.liveGus.contains("XPMultiplier=2.0"));
        assertFalse(cfg.liveGus.contains("XPMultiplier=1.0"));
    }

    @Test
    void applyPrefersCustomConfig() {
        InMemoryConfigStore cfg = new InMemoryConfigStore();
        cfg.gus.put("default", "[ServerSettings]\nXPMultiplier=2.0\n");
        cfg.mods.put("default", List.of("111"));
        cfg.maps.add("TheCenter");
        cfg.gus.put("TheCenter", "[ServerSettings]\nXPMultiplier=9.0\n");
        cfg.mods.put("TheCenter", List.of("999"));
        FakeEnv env = new FakeEnv();

        new ArkMapService(cfg, env).apply("TheCenter");

        assertEquals("999", env.values.get("ARK_MODS"));
        assertTrue(cfg.liveGus.contains("XPMultiplier=9.0"));
    }

    @Test
    void customizeThenUncustomize() {
        InMemoryConfigStore cfg = new InMemoryConfigStore();
        ArkMapService svc = new ArkMapService(cfg, new FakeEnv());
        assertTrue(svc.customize("Fjordur"));
        assertTrue(cfg.mapExists("Fjordur"));
        assertFalse(svc.customize("Fjordur"));
        assertTrue(svc.uncustomize("Fjordur"));
        assertFalse(cfg.mapExists("Fjordur"));
    }

    @Test
    void selectableMarksCustomVsDefault() {
        InMemoryConfigStore cfg = new InMemoryConfigStore();
        cfg.maps.add("TheCenter");
        List<MapChoice> sel = new ArkMapService(cfg, new FakeEnv()).selectable();
        assertTrue(sel.stream().anyMatch(c -> c.name().equals("TheCenter") && c.custom()));
        assertTrue(sel.stream().anyMatch(c -> c.name().equals("Ragnarok") && !c.custom()));
    }
}
