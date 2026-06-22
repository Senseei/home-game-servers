package com.senseei.launcher.ark.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Aggregate: the mod catalog. Owns its invariant (no duplicate ids) and the
 * id↔name knowledge; loaded and saved as a whole by a repository. Behaviour
 * lives here, not in a service.
 */
public final class ModRegistry {

    private final List<Mod> mods;

    public ModRegistry(List<Mod> mods) {
        this.mods = new ArrayList<>(mods);
    }

    public List<Mod> all() {
        return List.copyOf(mods);
    }

    public boolean contains(String id) {
        return mods.stream().anyMatch(m -> m.id().equals(id));
    }

    /** Adds a mod unless its id is already present (the catalog's invariant). */
    public void add(Mod mod) {
        if (!contains(mod.id())) {
            mods.add(mod);
        }
    }

    public String nameOf(String id) {
        return mods.stream().filter(m -> m.id().equals(id)).map(Mod::name).findFirst().orElse(id);
    }

    /** The given ids not yet in the catalog (de-duplicated). */
    public List<String> missing(Collection<String> ids) {
        return ids.stream().distinct().filter(id -> !contains(id)).toList();
    }
}
