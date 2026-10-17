package logs.benchmark;

import logs.common.LogEntry;
import logs.common.LogParser;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Baseline séquentielle : un seul thread, le MÊME parseur que tout le monde (logs.common.LogParser).
 * Sert de point de comparaison pour le benchmark (temps et débit avec 1 thread, sans concurrence).
 *
 * Usage : SequentialBaseline [access.log]
 */
public final class SequentialBaseline {

    private SequentialBaseline() { }

    public static void main(String[] args) throws Exception {
        Path logFile = Path.of(args.length > 0 ? args[0] : "site/logs/access.log");
        List<String> lines = ReferenceCounter.readLines(logFile);   // lecture AVANT le chrono : on mesure le calcul, pas le disque

        long valid = 0, invalid = 0, errors5xx = 0;
        Map<Integer, Long> statusCounts = new TreeMap<>();

        long start = System.nanoTime();
        for (String line : lines) {
            LogEntry e = LogParser.parse(line);
            if (e == null) {
                invalid++;
                continue;
            }
            valid++;
            statusCounts.merge(e.status, 1L, Long::sum);
            if (e.is5xx()) {
                errors5xx++;
            }
        }
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;

        System.out.println("=== BASELINE SEQUENTIELLE ===");
        System.out.println("Lignes lues : " + lines.size());
        System.out.println("Requêtes valides : " + valid + ", lignes ignorées : " + invalid);
        System.out.println("Erreurs 5xx : " + errors5xx);
        System.out.println("Statuts HTTP : " + statusCounts);
        System.out.printf(java.util.Locale.ROOT, "Temps : %.6f s, débit : %.0f lignes/s%n",
                seconds, seconds > 0 ? lines.size() / seconds : 0);
    }
}
