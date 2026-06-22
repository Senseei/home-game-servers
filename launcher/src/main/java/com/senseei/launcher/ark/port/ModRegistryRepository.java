package com.senseei.launcher.ark.port;

import com.senseei.launcher.ark.domain.ModRegistry;

/** Repository for the {@link ModRegistry} aggregate: load it, persist it. */
public interface ModRegistryRepository {

    ModRegistry load();

    void save(ModRegistry registry);
}
