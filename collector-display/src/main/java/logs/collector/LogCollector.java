package logs.collector;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collecteur : suit le fichier access.log du site (comme {@code tail -f}) et publie chaque ligne,
 * telle quelle, dans le topic "web-logs". Les moteurs A et B font eux-mêmes le parsing.
 *
 * Réglages possibles au lancement (sinon valeurs par défaut) :
 *   premier argument ou -Dlog.file=logs/access.log
 *   -Dkafka.bootstrap=localhost:9098,localhost:9094,localhost:9096
 *   -Dtopic.logs=web-logs
 *   -Dcollector.fromStart=true   (relire aussi ce qui est déjà dans le fichier ; défaut : false = nouvelles lignes seulement)
 *   -Dpoll.ms=200                (pause quand il n'y a rien de neuf)
 */
public class LogCollector {

    static final String BOOTSTRAP = System.getProperty("kafka.bootstrap", "localhost:9098,localhost:9094,localhost:9096");
    static final String TOPIC = System.getProperty("topic.logs", "web-logs");
    static final String LOG_FILE = System.getProperty("log.file", "logs/access.log");
    static final boolean FROM_START = Boolean.getBoolean("collector.fromStart");
    static final long POLL_MS = Long.getLong("poll.ms", 200L);

    private static volatile boolean running = true;

    static Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");               // même réglage que infra/config.properties
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // pas de doublons si Kafka réessaie
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);              // regroupe les lignes proches dans un même envoi
        return props;
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args.length > 0 ? args[0] : LOG_FILE);
        LogTailer tailer = new LogTailer(file, FROM_START);

        AtomicLong sent = new AtomicLong();
        AtomicLong failed = new AtomicLong();

        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            try {
                mainThread.join(5000);   // on laisse la boucle principale vider le producer proprement
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));

        System.out.println("[Collecteur] démarrage : " + file.toAbsolutePath() + " -> " + TOPIC
                           + " (Kafka " + BOOTSTRAP + ", " + (FROM_START ? "depuis le début" : "nouvelles lignes seulement") + ")");

        long lastReport = System.currentTimeMillis();
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            while (running) {
                List<String> lines;
                try {
                    lines = tailer.poll();
                } catch (IOException e) {
                    System.err.println("[Collecteur] lecture impossible (" + e.getMessage() + "), nouvel essai");
                    lines = List.of();
                }

                for (String line : lines) {
                    if (line.isBlank()) {
                        continue;
                    }
                    producer.send(new ProducerRecord<>(TOPIC, line), (meta, error) -> {
                        if (error == null) {
                            sent.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                            System.err.println("[Collecteur] échec d'envoi : " + error.getMessage());
                        }
                    });
                }

                long now = System.currentTimeMillis();
                if (now - lastReport >= 5000) {
                    System.out.println("[Collecteur] " + sent.get() + " lignes envoyées, " + failed.get() + " échecs");
                    lastReport = now;
                }

                if (lines.isEmpty()) {
                    try {
                        Thread.sleep(POLL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            producer.flush();
        }
        System.out.println("[Collecteur] arrêt : " + sent.get() + " lignes envoyées, " + failed.get() + " échecs");
    }
}
