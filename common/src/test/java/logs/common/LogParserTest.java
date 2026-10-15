package logs.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LogParserTest {

    /** Fabrique une ligne de log valide avec l'URL et le status voulus. */
    private static String ligne(String url, int status) {
        return "127.0.0.1 - - [07/Oct/2026:14:32:05 +0000] \"GET " + url + " HTTP/1.1\" " + status + " 123";
    }

    @Test
    void ligneValide() {
        LogEntry e = LogParser.parse(ligne("/checkout", 503));
        assertNotNull(e);
        assertEquals("127.0.0.1", e.ip);
        assertEquals("GET", e.method);
        assertEquals("/checkout", e.url);
        assertEquals(503, e.status);
        assertEquals(123L, e.bytes);
        assertEquals(Instant.parse("2026-10-07T14:32:05Z"), e.timestamp);
        assertTrue(e.is5xx());
    }

    @Test
    void erreur404N_estPasUneErreur5xx() {
        assertFalse(LogParser.parse(ligne("/inconnu", 404)).is5xx());
    }

    @Test
    void urlSansParametres() {
        assertEquals("/recherche", LogParser.parse(ligne("/recherche?q=chaise", 200)).url);
    }

    @Test
    void produitNormalise() {
        assertEquals("/produits/{id}", LogParser.parse(ligne("/produits/12?x=1", 200)).url);
    }

    @Test
    void fichiersStatiquesRegroupes() {
        assertEquals("/static", LogParser.parse(ligne("/css/site.css", 200)).url);
        assertEquals("/static", LogParser.parse(ligne("/logo.png", 200)).url);
    }

    @Test
    void slashFinalRetire() {
        assertEquals("/produits", LogParser.parse(ligne("/produits/", 200)).url);
    }

    @Test
    void tailleAvecTiret() {
        LogEntry e = LogParser.parse("127.0.0.1 - - [07/Oct/2026:14:32:05 +0000] \"GET / HTTP/1.1\" 200 -");
        assertNotNull(e);
        assertEquals(0L, e.bytes);
    }

    @Test
    void lignesMalFormeesDonnentNull() {
        assertNull(LogParser.parse("n'importe quoi"));
        assertNull(LogParser.parse(""));
        assertNull(LogParser.parse(null));
    }

    @Test
    void dateInvalideDonneNull() {
        assertNull(LogParser.parse("10.0.0.5 - - [32/Foo/2026:99:99:99 +0000] \"GET / HTTP/1.1\" 200 5"));
    }

    @Test
    void paramtreDeSessionRetire() {
        LogEntry e = LogParser.parse("0:0:0:0:0:0:0:1 - - [15/Oct/2026:09:23:26 +0100] "
                + "\"GET /produits;SOUKLINE_SESSION=2E80BDF2579692642B07B2021194C57B HTTP/1.1\" 200 21052");
        assertNotNull(e);
        assertEquals("/produits", e.url);
    }

    @Test
    void ligneTomcatReelleIpv6() {
        LogEntry e = LogParser.parse("0:0:0:0:0:0:0:1 - - [15/Oct/2026:09:24:37 +0100] \"POST /panier/vider HTTP/1.1\" 302 -");
        assertNotNull(e);
        assertEquals("0:0:0:0:0:0:0:1", e.ip);
        assertEquals("POST", e.method);
        assertEquals(0L, e.bytes);
    }
}
