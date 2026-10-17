package logs.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StatsMessageTest {

    @Test
    void parsesEngineAFormat() {
        StatsMessage m = StatsMessage.parse(
                "{\"engine\":\"A\",\"windowStart\":1791383525000,\"url\":\"/checkout\",\"requests\":3,\"errors5xx\":2}");
        assertNotNull(m);
        assertEquals("A", m.engine());
        assertEquals(1791383525000L, m.windowStart());
        assertEquals("/checkout", m.url());
        assertEquals(3L, m.requests());
        assertEquals(2L, m.errors5xx());
        assertTrue(m.isConsistent());
    }

    @Test
    void fieldOrderDoesNotMatter() {
        StatsMessage m = StatsMessage.parse(
                "{\"requests\":5,\"errors5xx\":1,\"url\":\"/\",\"windowStart\":10,\"engine\":\"B\"}");
        assertNotNull(m);
        assertEquals("B", m.engine());
        assertEquals(5L, m.requests());
    }

    @Test
    void missingFieldOrGarbageGivesNull() {
        assertNull(StatsMessage.parse("{\"engine\":\"A\",\"windowStart\":1,\"url\":\"/\",\"requests\":1}"));
        assertNull(StatsMessage.parse("pas du json"));
        assertNull(StatsMessage.parse(""));
        assertNull(StatsMessage.parse(null));
    }

    @Test
    void escapedUrlIsDecoded() {
        StatsMessage m = StatsMessage.parse(
                "{\"engine\":\"A\",\"windowStart\":1,\"url\":\"/a\\\"b\",\"requests\":1,\"errors5xx\":0}");
        assertNotNull(m);
        assertEquals("/a\"b", m.url());
    }

    @Test
    void inconsistentMessagesAreDetected() {
        assertFalse(new StatsMessage("A", 1, "/", 1, 2).isConsistent());    // plus d'erreurs que de requêtes
        assertFalse(new StatsMessage("A", 1, "/", -1, 0).isConsistent());   // compteur négatif
        assertFalse(new StatsMessage("A", 1, "", 1, 0).isConsistent());     // URL vide
    }
}
