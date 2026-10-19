package logs.benchmark;

import logs.benchmark.ReferenceCounter.Cell;
import logs.benchmark.ReferenceCounter.Key;
import logs.common.LogEntry;
import logs.common.LogParser;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

/**
 * Benchmark de DEBIT (lignes/s) en mémoire, avec 1 / 2 / 4 / 8 threads, sur un même access.log.
 *
 * IMPORTANT : les deux "moteurs" ci-dessous sont des MODÈLES EN MÉMOIRE de deux façons de compter
 * en parallèle. Ce ne sont PAS le moteur A (Kafka Streams) ni le moteur B (concurrence manuelle) :
 * ils ne passent pas par Kafka. Ils servent à comparer deux stratégies de compteurs et à valider
 * la méthode de mesure. Les vrais moteurs se mesurent de bout en bout (injecter N lignes dans
 * web-logs, chronométrer jusqu'à la fin) : étape à part.
 *
 * Chaque résultat est vérifié : il doit être IDENTIQUE à la baseline, case par case (fenêtre, URL).
 *
 * Usage : BenchmarkRunner [access.log] [runs=5] [warmup=2] [results/benchmark.csv]
 */
public class BenchmarkRunner {

    static final long WINDOW_MS = 5_000L;

    /** valid/invalid = lignes ; cells = résultat par (fenêtre, URL). */
    public record Result(long valid, long invalid, Map<Key, Cell> cells) { }

    public interface LogEngine {
        String name();
        Result run(List<String> lines, int threads) throws Exception;
    }

    private static Key keyOf(LogEntry e) {
        return new Key(ReferenceCounter.windowStart(e.timestamp.toEpochMilli(), WINDOW_MS), e.url);
    }

    // ---- baseline : 1 thread, une simple Map -------------------------------------------------
    static final LogEngine BASELINE = new LogEngine() {
        public String name() { return "baseline-sequentielle"; }
        public Result run(List<String> lines, int threads) {
            return countRange(lines, 0, lines.size());
        }
    };

    private static Result countRange(List<String> lines, int from, int to) {
        Map<Key, Cell> cells = new TreeMap<>();
        long valid = 0, invalid = 0;
        for (int i = from; i < to; i++) {
            if (lines.get(i).isBlank()) continue;
            LogEntry e = LogParser.parse(lines.get(i));
            if (e == null) { invalid++; continue; }
            valid++;
            cells.merge(keyOf(e), new Cell(1, e.is5xx() ? 1 : 0), Cell::plus);
        }
        return new Result(valid, invalid, cells);
    }

    // ---- modèle 1 : compteurs LOCAUX par thread, fusionnés à la fin (aucune contention) -------
    static final LogEngine LOCAL_COUNTERS = new LogEngine() {
        public String name() { return "modele-compteurs-locaux"; }
        public Result run(List<String> lines, int threads) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                List<Future<Result>> futures = new ArrayList<>();
                for (int[] r : split(lines.size(), threads)) {
                    futures.add(pool.submit(() -> countRange(lines, r[0], r[1])));
                }
                Map<Key, Cell> merged = new TreeMap<>();
                long valid = 0, invalid = 0;
                for (Future<Result> f : futures) {
                    Result part = f.get();
                    valid += part.valid();
                    invalid += part.invalid();
                    part.cells().forEach((k, v) -> merged.merge(k, v, Cell::plus));
                }
                return new Result(valid, invalid, merged);
            } finally {
                pool.shutdown();
            }
        }
    };

    // ---- modèle 2 : compteurs PARTAGES (ConcurrentHashMap + LongAdder), contention ------------
    static final LogEngine SHARED_COUNTERS = new LogEngine() {
        public String name() { return "modele-compteurs-partages"; }
        public Result run(List<String> lines, int threads) throws Exception {
            Map<Key, LongAdder[]> shared = new ConcurrentHashMap<>();
            LongAdder valid = new LongAdder(), invalid = new LongAdder();
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int[] r : split(lines.size(), threads)) {
                    futures.add(pool.submit(() -> {
                        for (int i = r[0]; i < r[1]; i++) {
                            if (lines.get(i).isBlank()) continue;
                            LogEntry e = LogParser.parse(lines.get(i));
                            if (e == null) { invalid.increment(); continue; }
                            valid.increment();
                            LongAdder[] c = shared.computeIfAbsent(keyOf(e),
                                    k -> new LongAdder[]{new LongAdder(), new LongAdder()});
                            c[0].increment();
                            if (e.is5xx()) c[1].increment();
                        }
                    }));
                }
                for (Future<?> f : futures) f.get();
            } finally {
                pool.shutdown();
            }
            Map<Key, Cell> cells = new TreeMap<>();
            shared.forEach((k, c) -> cells.put(k, new Cell(c[0].sum(), c[1].sum())));
            return new Result(valid.sum(), invalid.sum(), cells);
        }
    };

    /** Découpe [0,n) en 'parts' intervalles contigus. */
    static List<int[]> split(int n, int parts) {
        List<int[]> ranges = new ArrayList<>();
        int chunk = Math.max(1, (n + parts - 1) / parts);
        for (int s = 0; s < n; s += chunk) {
            ranges.add(new int[]{s, Math.min(n, s + chunk)});
        }
        return ranges;
    }

    public static void main(String[] args) throws Exception {
        Path log = Path.of(args.length > 0 ? args[0] : "site/logs/access.log");
        int runs = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        int warmup = args.length > 2 ? Integer.parseInt(args[2]) : 2;
        Path csv = Path.of(args.length > 3 ? args[3] : "results/benchmark.csv");
        int[] threadCounts = {1, 2, 4, 8};

        System.out.println("Chargement de " + log + " en mémoire (pour isoler le CPU du disque)...");
        List<String> lines = ReferenceCounter.readLines(log);
        System.out.println(lines.size() + " lignes chargées. CPU logiques de cette machine : "
                + Runtime.getRuntime().availableProcessors());

        Result reference = BASELINE.run(lines, 1);

        // Échauffement GLOBAL : la JVM compile le code à la volée (JIT). Sans cela, la baseline,
        // mesurée la première, serait pénalisée et tous les "speedups" seraient faussés.
        System.out.println("Échauffement de la JVM...");
        for (int i = 0; i < 3; i++) {
            BASELINE.run(lines, 1);
            LOCAL_COUNTERS.run(lines, 2);
            SHARED_COUNTERS.run(lines, 2);
        }

        if (csv.getParent() != null) Files.createDirectories(csv.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            // Mêmes colonnes qu'avant : plot_graphs.py n'a pas besoin de changer.
            w.println("engine,threads,lines,median_seconds,throughput_logs_per_s,speedup_vs_baseline,correct");
            double baseSec = measure(BASELINE, lines, 1, runs, warmup, reference, w, 0, true);
            for (LogEngine engine : List.of(LOCAL_COUNTERS, SHARED_COUNTERS)) {
                for (int t : threadCounts) {
                    measure(engine, lines, t, runs, warmup, reference, w, baseSec, false);
                }
            }
        }
        System.out.println("Résultats écrits dans " + csv);
        System.out.println("Graphiques : python plot_graphs.py " + csv + " results/graphs");
    }

    private static double measure(LogEngine engine, List<String> lines, int threads, int runs, int warmup,
                                  Result reference, PrintWriter w, double baselineSeconds,
                                  boolean isBaseline) throws Exception {
        boolean correct = true;
        for (int i = 0; i < warmup; i++) engine.run(lines, threads);

        double[] times = new double[runs];
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            Result r = engine.run(lines, threads);
            times[i] = (System.nanoTime() - t0) / 1e9;
            correct &= r.valid() == reference.valid()
                    && r.invalid() == reference.invalid()
                    && r.cells().equals(reference.cells());
        }
        Arrays.sort(times);
        double median = times[runs / 2];
        double throughput = median > 0 ? lines.size() / median : 0;
        double speedup = isBaseline ? 1.0 : (median > 0 ? baselineSeconds / median : 0);

        w.printf(Locale.ROOT, "\"%s\",%d,%d,%.6f,%.2f,%.3f,%s%n",
                engine.name(), threads, lines.size(), median, throughput, speedup, correct);
        w.flush();
        System.out.printf(Locale.ROOT, "%-30s threads=%d  %.3fs  %,.0f lignes/s  x%.2f  correct=%s%n",
                engine.name(), threads, median, throughput, speedup, correct);
        return median;
    }
}
