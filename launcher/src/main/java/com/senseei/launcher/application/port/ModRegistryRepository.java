package com.senseei.launcher.application.port;

import com.senseei.launcher.domain.ark.ModRegistry;

/** Repository for the {@link ModRegistry} aggregate: load it, persist it. */
public interface ModRegistryRepository {

    ModRegistry load();

    void save(ModRegistry registry);
}
