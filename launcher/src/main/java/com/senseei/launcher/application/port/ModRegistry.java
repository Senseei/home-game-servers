package com.senseei.launcher.application.port;

import com.senseei.launcher.domain.ark.Mod;

import java.util.List;

/** Port: the mod catalog (id → name), persisted as {@code mods.tsv}. */
public interface ModRegistry {

    /** All known mods, in registry order. */
    List<Mod> all();

    boolean contains(String id);

    /** Append a mod to the catalog. */
    void add(Mod mod);

    /** Look up a name; falls back to the id if unknown. */
    String nameOf(String id);
}
