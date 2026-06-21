package com.senseei.launcher.application.port;

import com.senseei.launcher.domain.Game;
import com.senseei.launcher.domain.RunState;

import java.io.InputStream;

/**
 * Port: runs the game containers. An adapter (docker compose) implements it;
 * the application depends only on this interface, never on infrastructure.
 */
public interface ContainerEngine {

    void up(Game game);

    void down(Game game);

    void restart(Game game);

    /** Runs a command inside the game's container (e.g. rcon-cli). */
    void exec(Game game, String... command);

    /** A live stream of the container's logs; the caller closes it. */
    InputStream logs(Game game, boolean follow, int tail);

    RunState state(Game game);
}
