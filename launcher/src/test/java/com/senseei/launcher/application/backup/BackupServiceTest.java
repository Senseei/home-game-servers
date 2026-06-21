package com.senseei.launcher.application.backup;

import com.senseei.launcher.application.port.Archiver;
import com.senseei.launcher.application.port.EnvStore;
import com.senseei.launcher.application.port.Flusher;
import com.senseei.launcher.application.port.LocalBackups;
import com.senseei.launcher.application.port.OffsiteBackups;
import com.senseei.launcher.domain.Game;
import com.senseei.launcher.domain.backup.Snapshot;
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

    static final class FakeArchiver implements Archiver {
        Path created;
        @Override public void create(Path source, Path dest) { created = dest; }
        @Override public void extract(Path archive, Path dest) { }
    }

    static final class InMemoryLocal implements LocalBackups {
        final List<Snapshot> snaps = new ArrayList<>();
        final Path dir = Path.of("/tmp/backups");
        @Override public Path pathFor(Game g, String f) { snaps.add(0, new Snapshot(g.name(), f)); return dir.resolve(f); }
        @Override public List<Snapshot> list(Game g) { return List.copyOf(snaps); }
        @Override public Path resolve(Snapshot s) { return dir.resolve(s.fileName()); }
        @Override public void delete(Snapshot s) { snaps.remove(s); }
    }

    static final class FakeOffsite implements OffsiteBackups {
        boolean on = false;
        final List<String> pushed = new ArrayList<>();
        @Override public boolean enabled() { return on; }
        @Override public void push(Game g, Path a) { pushed.add(0, a.getFileName().toString()); }
        @Override public List<String> list(Game g) { return List.copyOf(pushed); }
        @Override public void delete(Game g, String f) { pushed.remove(f); }
        @Override public void pull(Game g, String f, Path d) { }
    }

    static final class FakeEnv implements EnvStore {
        final Map<String, String> values = new HashMap<>();
        @Override public Optional<String> get(String k) { return Optional.ofNullable(values.get(k)); }
        @Override public void set(String k, String v) { values.put(k, v); }
    }

    private static BackupService service(InMemoryLocal local, FakeOffsite off, FakeEnv env,
                                         FakeFlusher flush, FakeArchiver arch) {
        return new BackupService(Path.of("/repo"), flush, arch, local, off, env,
                Clock.fixed(Instant.parse("2026-06-21T10:15:30Z"), ZoneOffset.UTC));
    }

    @Test
    void backupFlushesArchivesAndNamesSnapshot() {
        var local = new InMemoryLocal();
        var flush = new FakeFlusher();
        var arch = new FakeArchiver();
        Snapshot s = service(local, new FakeOffsite(), new FakeEnv(), flush, arch).backup("ark-se");

        assertEquals("ark-se", flush.flushed.name());
        assertTrue(s.fileName().startsWith("ark-se-2026"));
        assertTrue(s.fileName().endsWith(".tar.gz"));
        assertNotNull(arch.created);
    }

    @Test
    void rotatesLocalBeyondKeep() {
        var local = new InMemoryLocal();
        for (int i = 0; i < 8; i++) {
            local.snaps.add(new Snapshot("ark-se", "ark-se-old" + i + ".tar.gz"));
        }
        var env = new FakeEnv();
        env.set("BACKUP_KEEP_LOCAL", "3");

        service(local, new FakeOffsite(), env, new FakeFlusher(), new FakeArchiver()).backup("ark-se");

        assertEquals(3, local.snaps.size());
    }

    @Test
    void pushesOffsiteWhenEnabledAndSkipsWhenNot() {
        var enabled = new FakeOffsite();
        enabled.on = true;
        service(new InMemoryLocal(), enabled, new FakeEnv(), new FakeFlusher(), new FakeArchiver()).backup("ark-se");
        assertEquals(1, enabled.pushed.size());

        var disabled = new FakeOffsite();
        service(new InMemoryLocal(), disabled, new FakeEnv(), new FakeFlusher(), new FakeArchiver()).backup("ark-se");
        assertTrue(disabled.pushed.isEmpty());
    }
}
