package logs.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import logs.benchmark.ReferenceCounter.Cell;
import logs.benchmark.ReferenceCounter.Key;
import org.junit.jupiter.api.Test;

class EngineResultTest {

    static String msg(String engine, long w, String url, long req, long err) {
        return "{\"engine\":\"" + engine + "\",\"windowStart\":" + w + ",\"url\":\"" + url
             + "\",\"requests\":" + req + ",\"errors5xx\":" + err + "}";
    }

    @Test
    void lastUpdateOfACellWins() {
        // le moteur A republie la même case avec un total croissant : ce n'est pas un doublon
        EngineResult r = EngineResult.fromLines(List.of(
                msg("A", 0, "/x", 1, 0), msg("A", 0, "/x", 2, 1), msg("A", 0, "/x", 3, 1)), "A");
        assertEquals(new Cell(3, 1), r.cells.get(new Key(0, "/x")));
        assertEquals(1, r.cells.size());
        assertEquals(3L, r.messages);
        assertTrue(r.healthy());
    }

    @Test
    void decreasingCounterIsFlagged() {
        EngineResult r = EngineResult.fromLines(List.of(msg("A", 0, "/x", 5, 0), msg("A", 0, "/x", 4, 0)), "A");
        assertEquals(1L, r.decreasing);
        assertFalse(r.healthy());
    }

    @Test
    void wrongEngineUnreadableAndInconsistentAreFlagged() {
        EngineResult r = EngineResult.fromLines(List.of(
                msg("B", 0, "/x", 1, 0),            // mauvais moteur dans le topic
                "du bruit",                         // illisible
                msg("A", 0, "/y", 1, 2)), "A");     // plus d'erreurs que de requêtes
        assertEquals(1L, r.wrongEngine);
        assertEquals(1L, r.unreadable);
        assertEquals(1L, r.inconsistent);
        assertFalse(r.healthy());
    }

    @Test
    void blankLinesAreIgnored() {
        EngineResult r = EngineResult.fromLines(List.of("", "  ", msg("A", 0, "/x", 1, 0)), "A");
        assertEquals(0L, r.unreadable);
        assertTrue(r.healthy());
    }
}
