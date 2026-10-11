package logs.enginea;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Teste la topologie SANS Kafka : TopologyTestDriver simule Kafka en mémoire. */
class EngineATest {

    private static Properties props() {
        Properties p = new Properties();
        p.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        p.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        p.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        p.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);   // pas de cache : la sortie est immédiate
        return p;
    }

    private static String ligne(String url, int status, int seconde) {
        return "127.0.0.1 - - [07/Oct/2026:14:32:0" + seconde + " +0000] \"GET " + url
             + " HTTP/1.1\" " + status + " 10";
    }

    @Test
    void compteLesRequetesEtLesErreurs5xx() {
        try (TopologyTestDriver driver = new TopologyTestDriver(EngineA.buildTopology(), props())) {
            TestInputTopic<String, String> in =
                driver.createInputTopic("web-logs", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> out =
                driver.createOutputTopic("stats-a", new StringDeserializer(), new StringDeserializer());

            in.pipeInput(null, ligne("/checkout", 200, 5), Instant.parse("2026-10-07T14:32:05Z"));
            in.pipeInput(null, ligne("/checkout", 503, 7), Instant.parse("2026-10-07T14:32:07Z"));

            List<KeyValue<String, String>> res = out.readKeyValuesToList();
            KeyValue<String, String> derniere = res.get(res.size() - 1);   // dernière mise à jour
            assertEquals("/checkout", derniere.key);
            assertTrue(derniere.value.contains("\"requests\":2"));
            assertTrue(derniere.value.contains("\"errors5xx\":1"));
        }
    }

    @Test
    void lesUrlDeProduitsSontRegroupees() {
        try (TopologyTestDriver driver = new TopologyTestDriver(EngineA.buildTopology(), props())) {
            TestInputTopic<String, String> in =
                driver.createInputTopic("web-logs", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> out =
                driver.createOutputTopic("stats-a", new StringDeserializer(), new StringDeserializer());

            in.pipeInput(null, ligne("/produits/12", 200, 5), Instant.parse("2026-10-07T14:32:05Z"));
            in.pipeInput(null, ligne("/produits/13?x=1", 200, 6), Instant.parse("2026-10-07T14:32:06Z"));

            List<KeyValue<String, String>> res = out.readKeyValuesToList();
            KeyValue<String, String> derniere = res.get(res.size() - 1);
            assertEquals("/produits/{id}", derniere.key);
            assertTrue(derniere.value.contains("\"requests\":2"));
        }
    }

    @Test
    void uneLigneMalFormeeEstIgnoree() {
        try (TopologyTestDriver driver = new TopologyTestDriver(EngineA.buildTopology(), props())) {
            TestInputTopic<String, String> in =
                driver.createInputTopic("web-logs", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> out =
                driver.createOutputTopic("stats-a", new StringDeserializer(), new StringDeserializer());

            in.pipeInput(null, "n'importe quoi", Instant.parse("2026-10-07T14:32:05Z"));

            assertTrue(out.isEmpty());
        }
    }
}
