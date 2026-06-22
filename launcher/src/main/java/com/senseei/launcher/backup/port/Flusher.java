package com.senseei.launcher.backup.port;

import com.senseei.launcher.lifecycle.domain.Game;

/** Port: ask a game to flush its world to disk before a snapshot (best-effort). */
public interface Flusher {

    void flush(Game game);
}
