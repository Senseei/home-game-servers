package com.senseei.launcher.application;

import com.senseei.launcher.application.port.ContainerEngine;
import com.senseei.launcher.domain.GameCatalog;

import java.io.InputStream;
import java.util.List;

/**
 * Start / stop / restart / logs / status use-cases. Depends on the
 * {@link ContainerEngine} port; returns data, never formatted output, so any
 * presentation (CLI now, UI later) can drive it.
 */
public final class ServerLifecycle {

    private final ContainerEngine engine;

    public ServerLifecycle(ContainerEngine engine) {
        this.engine = engine;
    }

    public void start(String game) {
        engine.up(GameCatalog.lookup(game));
    }

    public void stop(String game) {
        engine.down(GameCatalog.lookup(game));
    }

    public void restart(String game) {
        engine.restart(GameCatalog.lookup(game));
    }

    public InputStream logs(String game, boolean follow, int tail) {
        return engine.logs(GameCatalog.lookup(game), follow, tail);
    }

    public List<ServerStatus> status() {
        return GameCatalog.all().stream()
                .map(g -> new ServerStatus(g.name(), engine.state(g)))
                .toList();
    }
}
