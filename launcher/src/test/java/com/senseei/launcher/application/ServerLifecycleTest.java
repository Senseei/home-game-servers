package com.senseei.launcher.application;

import com.senseei.launcher.application.port.ContainerEngine;
import com.senseei.launcher.domain.Game;
import com.senseei.launcher.domain.RunState;
import com.senseei.launcher.domain.UnknownGameException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerLifecycleTest {

    /** Test double for the port — no Docker needed. Implements the interface implicitly. */
    static final class FakeEngine implements ContainerEngine {
        final List<String> upped = new ArrayList<>();
        Map<String, RunState> states = Map.of();

        @Override public void up(Game g) { upped.add(g.name()); }
        @Override public void down(Game g) { }
        @Override public void restart(Game g) { }
        @Override public InputStream logs(Game g, boolean follow, int tail) { return InputStream.nullInputStream(); }
        @Override public RunState state(Game g) { return states.getOrDefault(g.name(), RunState.STOPPED); }
    }

    @Test
    void startCallsEngine() {
        FakeEngine fake = new FakeEngine();
        new ServerLifecycle(fake).start("ark-se");
        assertEquals(List.of("ark-se"), fake.upped);
    }

    @Test
    void startUnknownGameThrows() {
        assertThrows(UnknownGameException.class,
                () -> new ServerLifecycle(new FakeEngine()).start("nope"));
    }

    @Test
    void statusReportsAllGames() {
        FakeEngine fake = new FakeEngine();
        fake.states = Map.of("ark-se", RunState.RUNNING);
        List<ServerStatus> rows = new ServerLifecycle(fake).status();
        assertEquals(3, rows.size());
        assertTrue(rows.stream().anyMatch(r -> r.game().equals("ark-se") && r.state() == RunState.RUNNING));
    }
}
