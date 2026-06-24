package com.senseei.launcher.ark.app;

import com.senseei.launcher.ark.port.ModRegistryRepository;
import com.senseei.launcher.ark.port.WorkshopClient;
import com.senseei.launcher.ark.domain.MapConfig;
import com.senseei.launcher.ark.domain.Mod;
import com.senseei.launcher.ark.domain.ModRegistry;
import com.senseei.launcher.shared.port.EnvStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    static final class FakeEnv implements EnvStore {
        final Map<String, String> values = new HashMap<>();
        @Override public Optional<String> get(String k) { return Optional.ofNullable(values.get(k)); }
        @Override public void set(String k, String v) { values.put(k, v); }
    }

    private static InMemoryMapConfigRepository repoWithDefaultMods(List<String> mods) {
        return new InMemoryMapConfigRepository(new MapConfig("default", false, "", "", mods));
    }

    private static ModCatalogService service(ModRegistryRepository reg, WorkshopClient ws, InMemoryMapConfigRepository repo) {
        return new ModCatalogService(reg, ws, repo, new FakeEnv());
    }

    @Test
    void addResolvesNameAndDedups() {
        var reg = new InMemoryRegistryRepo();
        var ws = new FakeWorkshop();
        ws.titles = Map.of("731604991", "Structures Plus (S+)");
        var svc = service(reg, ws, repoWithDefaultMods(List.of()));

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
        var svc = service(reg, ws, repoWithDefaultMods(List.of("111", "222")));

        assertEquals(2, svc.syncRegistry());
        assertEquals("One", reg.load().nameOf("111"));
    }

    @Test
    void setMapModsCustomizesAndPersists() {
        var repo = repoWithDefaultMods(List.of("111"));
        service(new InMemoryRegistryRepo(), new FakeWorkshop(), repo)
                .setMapMods("Ragnarok", List.of("999", "888"));

        assertTrue(repo.exists("Ragnarok"));
        assertEquals(List.of("999", "888"), repo.load("Ragnarok").modIds());
    }

    @Test
    void toggleModAddsThenRemoves() {
        var repo = repoWithDefaultMods(List.of());
        var svc = service(new InMemoryRegistryRepo(), new FakeWorkshop(), repo);

        assertTrue(svc.toggleMod("default", "555"));
        assertEquals(List.of("555"), repo.load("default").modIds());
        assertEquals(false, svc.toggleMod("default", "555"));
        assertEquals(List.of(), repo.load("default").modIds());
    }

    @Test
    void removeModDropsFromRegistryAndEveryMap() {
        var reg = new InMemoryRegistryRepo();
        reg.registry.add(new Mod("111", "One"));
        reg.registry.add(new Mod("222", "Two"));
        var repo = repoWithDefaultMods(List.of("111", "222"));
        var svc = service(reg, new FakeWorkshop(), repo);
        svc.setMapMods("Ragnarok", List.of("111", "222"));   // a custom map carrying both

        svc.removeMod("111");

        assertEquals(false, reg.load().contains("111"));
        assertEquals(List.of("222"), repo.load("default").modIds());
        assertEquals(List.of("222"), repo.load("Ragnarok").modIds());
    }

    @Test
    void addModEnablesItInDefaultOnly() {
        var repo = repoWithDefaultMods(List.of());
        var svc = service(new InMemoryRegistryRepo(), new FakeWorkshop(), repo);

        svc.addMod("999");

        assertEquals(List.of("999"), repo.load("default").modIds());
    }

    @Test
    void modChangeSyncsTheActiveMapsModsEnv() {
        var repo = repoWithDefaultMods(List.of("111"));
        var env = new FakeEnv();
        env.set("ARK_MAP", "default");
        var svc = new ModCatalogService(new InMemoryRegistryRepo(), new FakeWorkshop(), repo, env);

        svc.toggleMod("default", "222");   // toggling the active map's mods updates ARK_MODS — no re-apply

        assertEquals("111,222", env.get("ARK_MODS").orElse(""));
    }
}
