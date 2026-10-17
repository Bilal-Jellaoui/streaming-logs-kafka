package logs.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import logs.benchmark.ReferenceCounter.Cell;
import logs.benchmark.ReferenceCounter.Key;
import logs.benchmark.ReferenceCounter.Reference;
import org.junit.jupiter.api.Test;

class ReferenceCounterTest {

    static String line(String hms, String url, int status) {
        return "127.0.0.1 - - [07/Oct/2026:" + hms + " +0000] \"GET " + url + " HTTP/1.1\" " + status + " 100";
    }

    static long ms(String hms) {
        return Instant.parse("2026-10-07T" + hms + "Z").toEpochMilli();
    }

    @Test
    void countsPerWindowAndUrl() {
        Reference r = ReferenceCounter.count(List.of(
                line("14:32:05", "/checkout", 200),
                line("14:32:06", "/checkout", 503),
                line("14:32:09", "/", 200),
                line("14:32:10", "/checkout", 503)), 5);

        assertEquals(4L, r.valid());
        assertEquals(new Cell(2, 1), r.cells().get(new Key(ms("14:32:05"), "/checkout")));
        assertEquals(new Cell(1, 0), r.cells().get(new Key(ms("14:32:05"), "/")));
        assertEquals(new Cell(1, 1), r.cells().get(new Key(ms("14:32:10"), "/checkout")));
        assertEquals(3, r.cells().size());
        assertEquals(2L, r.errors5xx());
    }

    @Test
    void invalidAndBlankLines() {
        Reference r = ReferenceCounter.count(List.of(line("14:32:05", "/", 200), "n'importe quoi", "", "   "), 5);
        assertEquals(2L, r.lines());     // les lignes vides ne comptent pas
        assertEquals(1L, r.valid());
        assertEquals(1L, r.invalid());
    }

    @Test
    void usesSharedParserNormalization() {
        // même normalisation que les moteurs : session retirée, produit regroupé, statique regroupé
        Reference r = ReferenceCounter.count(List.of(
                line("14:32:05", "/produits/2;SOUKLINE_SESSION=ABC", 200),
                line("14:32:05", "/produits/7?x=1", 200),
                line("14:32:05", "/css/soukline.css", 200)), 5);
        assertEquals(new Cell(2, 0), r.cells().get(new Key(ms("14:32:05"), "/produits/{id}")));
        assertEquals(new Cell(1, 0), r.cells().get(new Key(ms("14:32:05"), "/static")));
    }

    @Test
    void windowStartIsAlignedOnWindowSize() {
        assertEquals(ms("14:32:05"), ReferenceCounter.windowStart(ms("14:32:09") + 999, 5000));
        assertEquals(ms("14:32:10"), ReferenceCounter.windowStart(ms("14:32:10"), 5000));
        assertEquals(ms("14:32:00"), ReferenceCounter.windowStart(ms("14:32:04"), 5000));
    }

    @Test
    void timezoneOffsetIsConvertedToUtc() {
        // le site écrit en heure locale (+0100)
        String l = "127.0.0.1 - - [07/Oct/2026:15:32:05 +0100] \"GET / HTTP/1.1\" 200 5";
        Reference r = ReferenceCounter.count(List.of(l), 5);
        assertNotNull(r.cells().get(new Key(ms("14:32:05"), "/")));
    }
}
