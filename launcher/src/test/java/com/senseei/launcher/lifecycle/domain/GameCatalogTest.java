package com.senseei.launcher.lifecycle.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameCatalogTest {

    @Test
    void lookupResolvesKnownGame() {
        Game g = GameCatalog.lookup("ark-se");
        assertEquals("ark-server", g.container());
    }

    @Test
    void lookupThrowsOnUnknown() {
        assertThrows(UnknownGameException.class, () -> GameCatalog.lookup("nope"));
    }

    @Test
    void catalogHasThreeGames() {
        assertEquals(3, GameCatalog.all().size());
    }
}
