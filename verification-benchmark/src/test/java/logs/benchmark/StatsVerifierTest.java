package logs.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import logs.benchmark.Comparison.Mode;
import logs.benchmark.ReferenceCounter.Cell;
import logs.benchmark.ReferenceCounter.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatsVerifierTest {

    // 5 lignes valides + 1 invalide ; deux fenêtres de 5 s : 14:32:05 et 14:32:10
    static final List<String> LOG = List.of(
            ReferenceCounterTest.line("14:32:05", "/checkout", 200),
            ReferenceCounterTest.line("14:32:06", "/checkout", 503),
            ReferenceCounterTest.line("14:32:09", "/", 200),
            ReferenceCounterTest.line("14:32:10", "/checkout", 503),
            ReferenceCounterTest.line("14:32:11", "/produits/2", 200),
            "ligne invalide");

    static final long W1 = ReferenceCounterTest.ms("14:32:05");
    static final long W2 = ReferenceCounterTest.ms("14:32:10");

    /** Ce qu'un moteur PARFAIT publierait (avec une mise à jour intermédiaire pour /checkout). */
    static List<String> perfect(String engine) {
        return new ArrayList<>(List.of(
                EngineResultTest.msg(engine, W1, "/checkout", 1, 0),
                EngineResultTest.msg(engine, W1, "/checkout", 2, 1),
                EngineResultTest.msg(engine, W1, "/", 1, 0),
                EngineResultTest.msg(engine, W2, "/checkout", 1, 1),
                EngineResultTest.msg(engine, W2, "/produits/{id}", 1, 0)));
    }

    @Test
    void perfectEngineIsConformInBothModes() {
        for (Mode mode : Mode.values()) {
            StatsVerifier.Report r = StatsVerifier.verify(LOG, perfect("A"), null, 5, mode);
            assertTrue(r.ok(), "mode " + mode);
            assertEquals(1, r.verdicts().size());
        }
    }

    @Test
    void eachEngineIsComparedSeparatelyNotSummed() {
        // Régression de la 1re version : A et B comptent chacun la MOITIÉ des logs.
        // Leur somme égale la référence, mais aucun des deux n'est correct : il faut un échec.
        List<String> halfA = List.of(
                EngineResultTest.msg("A", W1, "/checkout", 2, 1),
                EngineResultTest.msg("A", W1, "/", 1, 0));
        List<String> halfB = List.of(
                EngineResultTest.msg("B", W2, "/checkout", 1, 1),
                EngineResultTest.msg("B", W2, "/produits/{id}", 1, 0));
        StatsVerifier.Report r = StatsVerifier.verify(LOG, halfA, halfB, 5, Mode.WINDOWS);
        assertFalse(r.ok());
        assertFalse(r.verdicts().get(0).ok());   // référence vs A
        assertFalse(r.verdicts().get(1).ok());   // référence vs B
    }

    @Test
    void oneWrongEngineDoesNotHideTheOtherOne() {
        List<String> wrongB = perfect("B");
        wrongB.set(2, EngineResultTest.msg("B", W1, "/", 2, 0));   // "/" compté 2 fois au lieu d'une
        StatsVerifier.Report r = StatsVerifier.verify(LOG, perfect("A"), wrongB, 5, Mode.WINDOWS);
        assertFalse(r.ok());
        assertTrue(r.verdicts().get(0).ok());    // A conforme
        assertFalse(r.verdicts().get(1).ok());   // B en écart
        assertFalse(r.verdicts().get(2).ok());   // et A != B
    }

    @Test
    void lostErrorIsDetected() {
        List<String> a = perfect("A");
        a.set(3, EngineResultTest.msg("A", W2, "/checkout", 1, 0));   // un 503 perdu
        assertFalse(StatsVerifier.verify(LOG, a, null, 5, Mode.TOTALS).ok());
    }

    @Test
    void windowShiftIsInvisibleInTotalsButCaughtInWindows() {
        // Le moteur a mis la requête de 14:32:09 ("/") dans la fenêtre suivante (heure d'envoi Kafka).
        List<String> shifted = perfect("A");
        shifted.set(2, EngineResultTest.msg("A", W2, "/", 1, 0));
        assertTrue(StatsVerifier.verify(LOG, shifted, null, 5, Mode.TOTALS).ok());
        StatsVerifier.Report strict = StatsVerifier.verify(LOG, shifted, null, 5, Mode.WINDOWS);
        assertFalse(strict.ok());
        assertTrue(strict.info().get(0).ok());   // le niveau "autre" (totaux) est affiché à titre indicatif
    }

    @Test
    void missingWindowIsDetected() {
        List<String> a = perfect("A");
        a.remove(4);   // /produits/{id} jamais publié
        assertFalse(StatsVerifier.verify(LOG, a, null, 5, Mode.TOTALS).ok());
    }

    @Test
    void unhealthyMessagesFailEvenIfValuesMatch() {
        List<String> a = perfect("A");
        a.add("du bruit dans le topic");
        assertFalse(StatsVerifier.verify(LOG, a, null, 5, Mode.TOTALS).ok());
    }

    @Test
    void atLeastOneEngineIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> StatsVerifier.verify(LOG, null, null, 5, Mode.TOTALS));
    }

    @Test
    void comparisonReportsMismatchRows() {
        var expected = new java.util.TreeMap<Key, Cell>();
        expected.put(new Key(0, "/x"), new Cell(2, 0));
        var actual = new java.util.TreeMap<Key, Cell>();
        actual.put(new Key(0, "/x"), new Cell(1, 0));
        Comparison.Verdict v = Comparison.compare("test", expected, actual, Mode.WINDOWS);
        assertFalse(v.ok());
        assertEquals(1, v.mismatches());
        assertEquals(2, v.rows().size());   // la ligne TOTAL + la case en écart
    }

    @Test
    void csvHasHeaderAndRows(@TempDir Path dir) throws IOException {
        StatsVerifier.Report r = StatsVerifier.verify(LOG, perfect("A"), null, 5, Mode.TOTALS);
        Path csv = dir.resolve("out/verification.csv");
        StatsVerifier.writeCsv(r, csv);
        List<String> lines = Files.readAllLines(csv);
        assertTrue(lines.get(0).startsWith("comparison,mode,check"));
        assertTrue(lines.size() > 2);
    }
}
