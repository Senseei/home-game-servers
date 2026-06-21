package com.senseei.launcher.application.ark;

import com.senseei.launcher.application.port.ModRegistryRepository;
import com.senseei.launcher.application.port.WorkshopClient;
import com.senseei.launcher.domain.ark.MapConfig;
import com.senseei.launcher.domain.ark.Mod;
import com.senseei.launcher.domain.ark.ModRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModCatalogServiceTest {

    static final class InMemoryRegistryRepo implements ModRegistryRepository {
        ModRegistry registry = new ModRegistry(new ArrayList<>());
        @Override public ModRegistry load() { return registry; }
        @Override public void save(ModRegistry r) { this.registry = r; }
    }

    static final class FakeWorkshop implements WorkshopClient {
        Map<String, String> titles = Map.of();
        @Override public Map<String, String> titles(List<String> ids) { return titles; }
    }

    private static InMemoryMapConfigRepository repoWithDefaultMods(List<String> mods) {
        return new InMemoryMapConfigRepository(new MapConfig("default", false, "", "", mods));
    }

    @Test
    void addResolvesNameAndDedups() {
        var reg = new InMemoryRegistryRepo();
        var ws = new FakeWorkshop();
        ws.titles = Map.of("731604991", "Structures Plus (S+)");
        var svc = new ModCatalogService(reg, ws, repoWithDefaultMods(List.of()));

        Mod added = svc.addMod("731604991");
        assertEquals("Structures Plus (S+)", added.name());
        assertTrue(reg.load().contains("731604991"));

        svc.addMod("731604991");
        assertEquals(1, reg.load().all().size());
    }

    @Test
    void syncFetchesMissingNames() {
        var reg = new InMemoryRegistryRepo();
        var ws = new FakeWorkshop();
        ws.titles = Map.of("111", "One", "222", "Two");
        var svc = new ModCatalogService(reg, ws, repoWithDefaultMods(List.of("111", "222")));

        assertEquals(2, svc.syncRegistry());
        assertEquals("One", reg.load().nameOf("111"));
    }

    @Test
    void setMapModsCustomizesAndPersists() {
        var repo = repoWithDefaultMods(List.of("111"));
        new ModCatalogService(new InMemoryRegistryRepo(), new FakeWorkshop(), repo)
                .setMapMods("Ragnarok", List.of("999", "888"));

        assertTrue(repo.exists("Ragnarok"));
        assertEquals(List.of("999", "888"), repo.load("Ragnarok").modIds());
    }
}
