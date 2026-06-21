package com.senseei.launcher.application.port;

import java.util.Optional;

/** Port: read/write namespaced knobs in the root {@code .env} (ARK_MAP, ARK_MODS, …). */
public interface EnvStore {

    Optional<String> get(String key);

    /** Sets a key, replacing the existing line or appending if absent. */
    void set(String key, String value);
}
