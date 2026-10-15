package logs.collector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatsJsonTest {

    @Test
    void litUnMessageDuMoteurA() {
        // Exactement le format produit par EngineA.toJson
        StatEvent e = StatsJson.parse("{\"engine\":\"A\",\"windowStart\":1790000000000,\"url\":\"/checkout\",\"requests\":3,\"errors5xx\":2}");

        assertNotNull(e);
        assertEquals("A", e.engine());
        assertEquals(1790000000000L, e.windowStart());
        assertEquals("/checkout", e.url());
        assertEquals(3, e.requests());
        assertEquals(2, e.errors5xx());
    }

    @Test
    void conserveLeNormalisationDesUrl() {
        StatEvent e = StatsJson.parse("{\"engine\":\"B\",\"windowStart\":5000,\"url\":\"/produits/{id}\",\"requests\":1,\"errors5xx\":0}");

        assertNotNull(e);
        assertEquals("B", e.engine());
        assertEquals("/produits/{id}", e.url());
    }

    @Test
    void ordreDesChampsSansImportance() {
        StatEvent e = StatsJson.parse("{\"errors5xx\":1,\"requests\":4,\"url\":\"/\",\"windowStart\":10,\"engine\":\"B\"}");

        assertNotNull(e);
        assertEquals(4, e.requests());
        assertEquals(1, e.errors5xx());
        assertEquals(10, e.windowStart());
    }

    @Test
    void annuleLEchappementDesGuillemetsEtAntislashs() {
        StatEvent e = StatsJson.parse("{\"engine\":\"A\",\"windowStart\":1,\"url\":\"/a\\\"b\\\\c\",\"requests\":1,\"errors5xx\":0}");

        assertNotNull(e);
        assertEquals("/a\"b\\c", e.url());
    }

    @Test
    void tolereLesEspaces() {
        StatEvent e = StatsJson.parse("{ \"engine\" : \"A\", \"windowStart\" : 7, \"url\" : \"/x\", \"requests\" : 2, \"errors5xx\" : 1 }");

        assertNotNull(e);
        assertEquals(7, e.windowStart());
        assertEquals(2, e.requests());
    }

    @Test
    void messageMalFormeDonneNull() {
        assertNull(StatsJson.parse(null));
        assertNull(StatsJson.parse(""));
        assertNull(StatsJson.parse("pas du json"));
        assertNull(StatsJson.parse("{\"engine\":\"A\",\"windowStart\":1,\"url\":\"/x\",\"requests\":2}"));   // errors5xx manque
    }

    @Test
    void nombreTropGrandDonneNull() {
        assertNull(StatsJson.parse("{\"engine\":\"A\",\"windowStart\":1,\"url\":\"/x\",\"requests\":99999999999999999999999,\"errors5xx\":0}"));
    }
}
