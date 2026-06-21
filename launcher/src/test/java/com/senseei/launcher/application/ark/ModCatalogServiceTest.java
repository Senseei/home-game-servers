package com.senseei.launcher.application.ark;

import com.senseei.launcher.application.port.ModRegistry;
import com.senseei.launcher.application.port.WorkshopClient;
import com.senseei.launcher.domain.ark.Mod;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModCatalogServiceTest {

    static final class FakeRegistry implements ModRegistry {
        final List<Mod> mods = new ArrayList<>();
        @Override public List<Mod> all() { return mods; }
        @Override public boolean contains(String id) { return mods.stream().anyMatch(m -> m.id().equals(id)); }
        @Override public void add(Mod m) { mods.add(m); }
        @Override public String nameOf(String id) {
            return mods.stream().filter(m -> m.id().equals(id)).map(Mod::name).findFirst().orElse(id);
        }
    }

    static final class FakeWorkshop implements WorkshopClient {
        Map<String, String> titles = Map.of();
        @Override public Map<String, String> titles(List<String> ids) { return titles; }
    }

    @Test
    void addResolvesNameFromSteam() {
        FakeRegistry reg = new FakeRegistry();
        FakeWorkshop ws = new FakeWorkshop();
        ws.titles = Map.of("731604991", "Structures Plus (S+)");

        Mod added = new ModCatalogService(reg, ws, new InMemoryConfigStore()).addMod("731604991");

        assertEquals("Structures Plus (S+)", added.name());
        assertTrue(reg.contains("731604991"));
    }

    @Test
    void addIsIdempotent() {
        FakeRegistry reg = new FakeRegistry();
        reg.add(new Mod("111", "Already"));
        new ModCatalogService(reg, new FakeWorkshop(), new InMemoryConfigStore()).addMod("111");
        assertEquals(1, reg.all().size());
    }

    @Test
    void syncFetchesMissingNames() {
        FakeRegistry reg = new FakeRegistry();
        FakeWorkshop ws = new FakeWorkshop();
        ws.titles = Map.of("111", "One", "222", "Two");
        InMemoryConfigStore cfg = new InMemoryConfigStore();
        cfg.mods.put("default", List.of("111", "222"));

        int added = new ModCatalogService(reg, ws, cfg).syncRegistry();

        assertEquals(2, added);
        assertEquals("One", reg.nameOf("111"));
    }
}
