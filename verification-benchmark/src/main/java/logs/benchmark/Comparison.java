package logs.benchmark;

import logs.benchmark.ReferenceCounter.Cell;
import logs.benchmark.ReferenceCounter.Key;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compare deux tableaux de résultats (attendu / obtenu), case par case.
 *
 * Deux niveaux de précision :
 *  - WINDOWS : une case = (fenêtre de 5 s, URL). C'est la vérification stricte.
 *  - TOTALS  : une case = une URL, toutes fenêtres confondues. Elle vérifie que le moteur compte
 *              juste, même si les fenêtres sont décalées (voir la note dans StatsVerifier).
 */
public final class Comparison {

    public enum Mode { TOTALS, WINDOWS }

    public record Row(String check, Cell expected, Cell actual, boolean ok) { }

    public record Verdict(String label, Mode mode, boolean ok, List<Row> rows, int keysChecked, int mismatches) { }

    private Comparison() { }

    public static Verdict compare(String label, Map<Key, Cell> expected, Map<Key, Cell> actual, Mode mode) {
        Map<Key, Cell> e = mode == Mode.TOTALS ? byUrl(expected) : expected;
        Map<Key, Cell> a = mode == Mode.TOTALS ? byUrl(actual) : actual;

        Set<Key> keys = new TreeSet<>(e.keySet());
        keys.addAll(a.keySet());

        Cell totalExpected = sum(e);
        Cell totalActual = sum(a);
        boolean totalOk = totalExpected.equals(totalActual);

        List<Row> rows = new ArrayList<>();
        rows.add(new Row("TOTAL", totalExpected, totalActual, totalOk));

        int mismatches = 0;
        for (Key k : keys) {
            Cell x = e.getOrDefault(k, Cell.ZERO);
            Cell y = a.getOrDefault(k, Cell.ZERO);
            if (!x.equals(y)) {
                mismatches++;
                rows.add(new Row(describe(k, mode), x, y, false));
            }
        }
        return new Verdict(label, mode, totalOk && mismatches == 0, rows, keys.size(), mismatches);
    }

    /** Additionne toutes les fenêtres d'une même URL (la clé devient (0, url)). */
    static Map<Key, Cell> byUrl(Map<Key, Cell> cells) {
        Map<Key, Cell> out = new TreeMap<>();
        cells.forEach((k, v) -> out.merge(new Key(0, k.url()), v, Cell::plus));
        return out;
    }

    private static Cell sum(Map<Key, Cell> cells) {
        Cell s = Cell.ZERO;
        for (Cell c : cells.values()) {
            s = s.plus(c);
        }
        return s;
    }

    private static String describe(Key k, Mode mode) {
        return mode == Mode.TOTALS ? k.url()
             : "fenêtre " + Instant.ofEpochMilli(k.windowStart()) + " | " + k.url();
    }
}
