package com.senseei.launcher.application.ark;

import com.senseei.launcher.application.port.ConfigStore;
import com.senseei.launcher.application.port.ModRegistry;
import com.senseei.launcher.application.port.WorkshopClient;
import com.senseei.launcher.domain.ark.Mod;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Use-cases for the mod catalog: list, add (Steam-resolved), sync, set a map's mods. */
public final class ModCatalogService {

    private static final String DEFAULT = "default";

    private final ModRegistry registry;
    private final WorkshopClient workshop;
    private final ConfigStore config;

    public ModCatalogService(ModRegistry registry, WorkshopClient workshop, ConfigStore config) {
        this.registry = registry;
        this.workshop = workshop;
        this.config = config;
    }

    public List<Mod> catalog() {
        return registry.all();
    }

    /** The effective mods for a map (its custom list if any, else default), named. */
    public List<Mod> modsOf(String map) {
        String source = config.mapExists(map) ? map : DEFAULT;
        List<Mod> out = new ArrayList<>();
        for (String id : config.readModIds(source)) {
            out.add(new Mod(id, registry.nameOf(id)));
        }
        return out;
    }

    /** Adds one mod to the registry by id, resolving its name from Steam. Idempotent. */
    public Mod addMod(String id) {
        if (registry.contains(id)) {
            return new Mod(id, registry.nameOf(id));
        }
        String name = workshop.titles(List.of(id)).getOrDefault(id, "(unknown)");
        Mod mod = new Mod(id, name);
        registry.add(mod);
        return mod;
    }

    /** Resolves names for any ids used (default + maps) not yet in the registry. Returns how many added. */
    public int syncRegistry() {
        Set<String> used = new LinkedHashSet<>(config.readModIds(DEFAULT));
        for (String map : config.customMaps()) {
            used.addAll(config.readModIds(map));
        }
        List<String> missing = used.stream().filter(id -> !registry.contains(id)).toList();
        if (missing.isEmpty()) {
            return 0;
        }
        Map<String, String> titles = workshop.titles(missing);
        for (String id : missing) {
            registry.add(new Mod(id, titles.getOrDefault(id, "(unknown)")));
        }
        return missing.size();
    }

    /** Sets a target's mod list ({@code "default"} or a map, customizing the map first if needed). */
    public void setMapMods(String target, List<String> ids) {
        if (!target.equals(DEFAULT) && !config.mapExists(target)) {
            config.copyDefaultTo(target);
        }
        config.writeModIds(target, ids);
    }
}
