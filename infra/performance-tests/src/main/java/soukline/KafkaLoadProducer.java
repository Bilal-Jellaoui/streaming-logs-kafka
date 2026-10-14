package soukline;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

/** Envoie un lot de messages de diagnostic, puis mesure les confirmations Kafka. */
public final class KafkaLoadProducer {
    private static final int DEFAULT_COUNT = 10_000;

    public static void main(String[] args) throws Exception {
        int count = parseCount(args);
        KafkaConfig config = new KafkaConfig("../config.properties");
        String testId = UUID.randomUUID().toString();
        System.out.println("=== SOUKLINE KAFKA LOAD PRODUCER ===");
        System.out.println("Test ID : " + testId);
        System.out.println("Messages demandes : " + count);

        ProducerResult result = produce(config, testId, count);
        printResults(testId, result);
        if (result.success != count || result.errors != 0) {
            throw new IllegalStateException("Production incomplete.");
        }
    }

    private static int parseCount(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: KafkaLoadProducer [nombreMessages]");
        }
        int count = args.length == 0 ? DEFAULT_COUNT : Integer.parseInt(args[0]);
        if (count <= 0) {
            throw new IllegalArgumentException("Le nombre de messages doit etre positif.");
        }
        return count;
    }

    private static Properties producerProperties(KafkaConfig config) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, config.getProducerAcks());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        return props;
    }

    private static ProducerResult produce(KafkaConfig config, String testId, int count) {
        AtomicInteger acknowledged = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        long[] latencies = new long[count];
        long start = System.nanoTime();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties(config))) {
            for (int i = 0; i < count; i++) {
                final int index = i;
                final long sentAt = System.nanoTime();
                String value = "SOUKLINE-LOAD|" + testId + "|" + i;
                // Cle variable : les messages peuvent etre repartis entre partitions.
                ProducerRecord<String, String> record =
                    new ProducerRecord<>(config.getLogsTopic(), Integer.toString(i), value);
                producer.send(record, (metadata, error) -> {
                    if (error == null && metadata != null) {
                        latencies[index] = System.nanoTime() - sentAt;
                        acknowledged.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                        System.err.println("Echec message " + index + " : " + error);
                    }
                });
            }
            producer.flush();
        }
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        return new ProducerResult(count, acknowledged.get(), failed.get(), seconds, latencies);
    }

    private static void printResults(String testId, ProducerResult result) {
        long[] valid = LongStream.of(result.latencies).filter(x -> x > 0).sorted().toArray();
        double averageMs = valid.length == 0 ? 0 : Arrays.stream(valid).average().orElse(0) / 1_000_000.0;
        System.out.println("=== RESULTATS PRODUCER ===");
        System.out.println("Messages demandes : " + result.requested);
        System.out.println("Messages confirmes : " + result.success);
        System.out.println("Erreurs : " + result.errors);
        System.out.printf("Duree : %.3f s%n", result.seconds);
        System.out.printf("Debit : %.2f msg/s%n", result.success / result.seconds);
        System.out.printf("Latence moyenne : %.3f ms%n", averageMs);
        System.out.printf("Latence P95 : %.3f ms%n", percentile(valid, .95));
        System.out.printf("Latence P99 : %.3f ms%n", percentile(valid, .99));
        System.out.println("Test ID : " + testId);
        System.out.println(result.success == result.requested && result.errors == 0 ? "PASS" : "FAIL");
    }

    private static double percentile(long[] sorted, double quantile) {
        if (sorted.length == 0) return 0;
        int index = Math.max(0, (int) Math.ceil(quantile * sorted.length) - 1);
        return sorted[index] / 1_000_000.0;
    }

    private static final class ProducerResult {
        final int requested;
        final int success;
        final int errors;
        final double seconds;
        final long[] latencies;

        ProducerResult(int requested, int success, int errors, double seconds, long[] latencies) {
            this.requested = requested;
            this.success = success;
            this.errors = errors;
            this.seconds = seconds;
            this.latencies = latencies;
        }
    }
}
