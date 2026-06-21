package com.senseei.launcher.application.port;

import com.senseei.launcher.domain.Game;

/** Port: ask a game to flush its world to disk before a snapshot (best-effort). */
public interface Flusher {

    void flush(Game game);
}
