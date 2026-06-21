package com.senseei.launcher.domain;

import java.util.List;

/** The set of games this tool manages, with lookup. Pure — no I/O, fully testable. */
public final class GameCatalog {

    private static final List<Game> GAMES = List.of(
            new Game("minecraft", "mc", "games/minecraft"),
            new Game("palworld", "palworld-server", "games/palworld"),
            new Game("ark-se", "ark-server", "games/ark-se")
    );

    private GameCatalog() {
    }

    public static List<Game> all() {
        return GAMES;
    }

    public static List<String> names() {
        return GAMES.stream().map(Game::name).toList();
    }

    public static Game lookup(String name) {
        return GAMES.stream()
                .filter(g -> g.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new UnknownGameException(name, names()));
    }
}
