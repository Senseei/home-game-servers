package com.senseei.launcher.ark.app;

import com.senseei.launcher.ark.port.MapConfigRepository;
import com.senseei.launcher.ark.port.ModRegistryRepository;
import com.senseei.launcher.ark.port.WorkshopClient;
import com.senseei.launcher.ark.domain.MapConfig;
import com.senseei.launcher.ark.domain.Mod;
import com.senseei.launcher.ark.domain.ModRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Use-cases for the mod catalog — thin orchestration over the {@link ModRegistry}
 * and {@link MapConfig} aggregates (the dedup invariant lives in the aggregate).
 */
public final class ModCatalogService {

    private static final String DEFAULT = "default";

    private final ModRegistryRepository registries;
    private final WorkshopClient workshop;
    private final MapConfigRepository maps;

    public ModCatalogService(ModRegistryRepository registries, WorkshopClient workshop, MapConfigRepository maps) {
        this.registries = registries;
        this.workshop = workshop;
        this.maps = maps;
    }

    public List<Mod> catalog() {
        return registries.load().all();
    }

    public List<Mod> modsOf(String map) {
        ModRegistry registry = registries.load();
        List<Mod> out = new ArrayList<>();
        for (String id : maps.load(map).modIds()) {
            out.add(new Mod(id, registry.nameOf(id)));
        }
        return out;
    }

    public Mod addMod(String id) {
        ModRegistry registry = registries.load();
        if (registry.contains(id)) {
            return new Mod(id, registry.nameOf(id));
        }
        Mod mod = new Mod(id, workshop.titles(List.of(id)).getOrDefault(id, "(unknown)"));
        registry.add(mod);
        registries.save(registry);
        return mod;
    }

    public int syncRegistry() {
        ModRegistry registry = registries.load();
        Set<String> used = new LinkedHashSet<>(maps.load(DEFAULT).modIds());
        for (String map : maps.customMaps()) {
            used.addAll(maps.load(map).modIds());
        }
        List<String> missing = registry.missing(used);
        if (missing.isEmpty()) {
            return 0;
        }
        Map<String, String> titles = workshop.titles(missing);
        missing.forEach(id -> registry.add(new Mod(id, titles.getOrDefault(id, "(unknown)"))));
        registries.save(registry);
        return missing.size();
    }

    /** Sets a target's mods ({@code "default"} or a map; saving a map customizes it). */
    public void setMapMods(String target, List<String> ids) {
        MapConfig cfg = maps.load(target);
        cfg.setMods(ids);
        maps.save(cfg);
    }

    /** Toggles one mod for a target (add if absent, remove if present); returns its new state. */
    public boolean toggleMod(String target, String id) {
        MapConfig cfg = maps.load(target);
        List<String> ids = new ArrayList<>(cfg.modIds());
        boolean nowOn = !ids.remove(id);
        if (nowOn) {
            ids.add(id);
        }
        cfg.setMods(ids);
        maps.save(cfg);
        return nowOn;
    }

    /** Removes a mod from the registry AND from every config that uses it (default + custom maps). */
    public Mod removeMod(String id) {
        ModRegistry registry = registries.load();
        Mod removed = new Mod(id, registry.nameOf(id));
        registry.remove(id);
        registries.save(registry);
        dropFromConfig(DEFAULT, id);
        for (String map : maps.customMaps()) {
            dropFromConfig(map, id);
        }
        return removed;
    }

    private void dropFromConfig(String target, String id) {
        MapConfig cfg = maps.load(target);
        if (cfg.modIds().contains(id)) {
            List<String> ids = new ArrayList<>(cfg.modIds());
            ids.remove(id);
            cfg.setMods(ids);
            maps.save(cfg);
        }
    }
}
