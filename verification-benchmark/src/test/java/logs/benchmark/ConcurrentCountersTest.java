package logs.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Test;

/**
 * Les 3 techniques de compteurs concurrents que les moteurs peuvent utiliser doivent être exactes
 * sous charge (8 threads x 1 million d'incrémentations).
 */
class ConcurrentCountersTest {

    private static final int THREADS = 8;
    private static final int INCREMENTS = 1_000_000;

    private static void runConcurrently(Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }
                task.run();
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "timeout du test de charge");
    }

    @Test
    void atomicLongIsExact() throws Exception {
        AtomicLong c = new AtomicLong();
        runConcurrently(() -> { for (int i = 0; i < INCREMENTS; i++) c.incrementAndGet(); });
        assertEquals((long) THREADS * INCREMENTS, c.get());
    }

    @Test
    void longAdderIsExact() throws Exception {
        LongAdder c = new LongAdder();
        runConcurrently(() -> { for (int i = 0; i < INCREMENTS; i++) c.increment(); });
        assertEquals((long) THREADS * INCREMENTS, c.sum());
    }

    @Test
    void concurrentHashMapOfAddersIsExact() throws Exception {
        Map<String, LongAdder> map = new ConcurrentHashMap<>();
        runConcurrently(() -> {
            for (int i = 0; i < INCREMENTS; i++) map.computeIfAbsent("/x", k -> new LongAdder()).increment();
        });
        assertEquals((long) THREADS * INCREMENTS, map.get("/x").sum());
    }
}
