package com.senseei.launcher.backup.app;

import com.senseei.launcher.backup.port.BackupStore;
import com.senseei.launcher.shared.port.EnvStore;
import com.senseei.launcher.backup.port.Flusher;
import com.senseei.launcher.lifecycle.domain.Game;
import com.senseei.launcher.backup.domain.Snapshot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupServiceTest {

    static final class FakeFlusher implements Flusher {
        Game flushed;
        @Override public void flush(Game g) { flushed = g; }
    }

    static final class FakeStore implements BackupStore {
        final List<Snapshot> localSnaps = new ArrayList<>();
        final List<String> pushed = new ArrayList<>();
        boolean offsite = false;
        Snapshot archived;

        @Override public Snapshot archive(Game g, String f, Path src) {
            Snapshot s = new Snapshot(g.name(), f);
            localSnaps.add(0, s);
            archived = s;
            return s;
        }
        @Override public List<Snapshot> localSnapshots(Game g) { return List.copyOf(localSnaps); }
        @Override public void deleteLocal(Snapshot s) { localSnaps.remove(s); }
        @Override public void extract(Snapshot s, Path d) { }
        @Override public boolean offsiteEnabled() { return offsite; }
        @Override public void pushOffsite(Snapshot s) { pushed.add(0, s.fileName()); }
        @Override public List<String> offsiteSnapshots(Game g) { return List.copyOf(pushed); }
        @Override public void deleteOffsite(Game g, String f) { pushed.remove(f); }
        @Override public void pullOffsite(Game g, String f) { }
    }

    static final class FakeEnv implements EnvStore {
        final Map<String, String> values = new HashMap<>();
        @Override public Optional<String> get(String k) { return Optional.ofNullable(values.get(k)); }
        @Override public void set(String k, String v) { values.put(k, v); }
    }

    private static BackupService service(FakeStore store, FakeFlusher flusher, FakeEnv env) {
        return new BackupService(Path.of("/repo"), flusher, store, env,
                Clock.fixed(Instant.parse("2026-06-21T10:15:30Z"), ZoneOffset.UTC));
    }

    @Test
    void backupFlushesArchivesAndNames() {
        FakeStore store = new FakeStore();
        FakeFlusher flusher = new FakeFlusher();
        Snapshot s = service(store, flusher, new FakeEnv()).backup("ark-se");

        assertEquals("ark-se", flusher.flushed.name());
        assertTrue(s.fileName().startsWith("ark-se-2026"));
        assertTrue(s.fileName().endsWith(".tar.gz"));
        assertNotNull(store.archived);
    }

    @Test
    void rotatesLocalBeyondKeep() {
        FakeStore store = new FakeStore();
        for (int i = 0; i < 8; i++) {
            store.localSnaps.add(new Snapshot("ark-se", "ark-se-old" + i + ".tar.gz"));
        }
        FakeEnv env = new FakeEnv();
        env.set("BACKUP_KEEP_LOCAL", "3");

        service(store, new FakeFlusher(), env).backup("ark-se");

        assertEquals(3, store.localSnaps.size());
    }

    @Test
    void pushesOffsiteOnlyWhenEnabled() {
        FakeStore on = new FakeStore();
        on.offsite = true;
        service(on, new FakeFlusher(), new FakeEnv()).backup("ark-se");
        assertEquals(1, on.pushed.size());

        FakeStore off = new FakeStore();
        service(off, new FakeFlusher(), new FakeEnv()).backup("ark-se");
        assertTrue(off.pushed.isEmpty());
    }
}
