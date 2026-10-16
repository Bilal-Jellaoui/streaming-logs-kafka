package logs.engineb;

import logs.common.LogEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class WindowCountersTest {

    /** Fenêtres de 5 s. 10:00:00 et 10:00:04 sont dans la même fenêtre, 10:00:05 dans la suivante. */
    private static LogEntry entry(String heure, String url, int status) {
        return new LogEntry("127.0.0.1", Instant.parse("2026-10-16T" + heure + "Z"), "GET", url, status, 100);
    }

    @Test
    void compteLesRequetesParUrl() {
        WindowCounters c = new WindowCounters(5);
        c.record(entry("10:00:00", "/produits", 200));
        c.record(entry("10:00:01", "/produits", 200));
        c.record(entry("10:00:02", "/checkout", 200));

        List<WindowCounters.Snapshot> res = c.drainUpdates();
        assertEquals(2, res.size());
        assertEquals("/checkout", res.get(0).url());
        assertEquals(1, res.get(0).requests());
        assertEquals("/produits", res.get(1).url());
        assertEquals(2, res.get(1).requests());
    }

    @Test
    void compteLesErreurs5xxMaisPasLes404() {
        WindowCounters c = new WindowCounters(5);
        c.record(entry("10:00:00", "/checkout", 503));
        c.record(entry("10:00:01", "/checkout", 503));
        c.record(entry("10:00:02", "/checkout", 404));

        WindowCounters.Snapshot s = c.drainUpdates().get(0);
        assertEquals(3, s.requests());
        assertEquals(2, s.errors5xx());
    }

    @Test
    void separeLesFenetresDe5Secondes() {
        WindowCounters c = new WindowCounters(5);
        c.record(entry("10:00:00", "/", 200));
        c.record(entry("10:00:04", "/", 200));
        c.record(entry("10:00:05", "/", 200));

        List<WindowCounters.Snapshot> res = c.drainUpdates();
        assertEquals(2, res.size());
        assertEquals(2, res.get(0).requests());
        assertEquals(1, res.get(1).requests());
        assertEquals(5000L, res.get(1).windowStart() - res.get(0).windowStart());
    }

    @Test
    void republieLeTotalSeulementPourCeQuiChange() {
        WindowCounters c = new WindowCounters(5);
        c.record(entry("10:00:00", "/a", 200));
        c.record(entry("10:00:00", "/b", 200));
        assertEquals(2, c.drainUpdates().size());
        assertTrue(c.drainUpdates().isEmpty());          // rien de neuf

        c.record(entry("10:00:01", "/a", 200));
        List<WindowCounters.Snapshot> res = c.drainUpdates();
        assertEquals(1, res.size());                     // seulement /a
        assertEquals(2, res.get(0).requests());          // le TOTAL (2), pas la différence (1)
    }

    @Test
    void comptageExactAvecPlusieursThreads() throws Exception {
        WindowCounters c = new WindowCounters(5);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> taches = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            taches.add(pool.submit(() -> {
                for (int i = 0; i < 10_000; i++) {
                    c.record(entry("10:00:01", "/checkout", i % 10 == 0 ? 503 : 200));
                }
            }));
        }
        for (Future<?> f : taches) {
            f.get();
        }
        pool.shutdown();

        WindowCounters.Snapshot s = c.drainUpdates().get(0);
        assertEquals(80_000, s.requests());      // aucun +1 perdu
        assertEquals(8_000, s.errors5xx());
    }

    @Test
    void oublieLesVieillesFenetres() {
        WindowCounters c = new WindowCounters(5);
        c.record(entry("10:00:00", "/vieux", 200));
        c.record(entry("10:10:00", "/recent", 200));     // 120 fenêtres plus tard
        c.purgeKeeping(10);
        assertEquals(1, c.totalRequests());
        List<WindowCounters.Snapshot> res = c.drainUpdates();
        assertEquals(1, res.size());
        assertEquals("/recent", res.get(0).url());
    }

    @Test
    void jsonIdentiqueAuMoteurASaufLeNom() {
        WindowCounters.Snapshot s = new WindowCounters.Snapshot(1792016400000L, "/checkout", 3, 2);
        assertEquals("{\"engine\":\"B\",\"windowStart\":1792016400000,\"url\":\"/checkout\",\"requests\":3,\"errors5xx\":2}",
                     s.toJson());
    }

    @Test
    void jsonEchappeLesGuillemetsDansUneUrl() {
        WindowCounters.Snapshot s = new WindowCounters.Snapshot(0L, "/a\"b", 1, 0);
        assertTrue(s.toJson().contains("\"url\":\"/a\\\"b\""));
    }
}
