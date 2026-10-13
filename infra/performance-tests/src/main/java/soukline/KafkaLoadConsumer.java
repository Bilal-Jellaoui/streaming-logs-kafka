package soukline;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Verifie les messages d'un test de diagnostic Kafka deja termine. */
public final class KafkaLoadConsumer {
    private static final long TIMEOUT_MS = 120_000;

    public static void main(String[] args) throws Exception {
        TestRequest request = parseArguments(args);
        KafkaConfig config = new KafkaConfig("../config.properties");
        System.out.println("=== VERIFICATION KAFKA ===");
        System.out.println("Test ID : " + request.testId);
        System.out.println("Messages attendus : " + request.expected);

        VerificationResult result = verify(config, request);
        printResults(result);
        if (!result.passed()) {
            throw new IllegalStateException("Verification Kafka incomplete.");
        }
    }

    private static TestRequest parseArguments(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: KafkaLoadConsumer <testId> <nombreMessages>");
        }
        int expected = Integer.parseInt(args[1]);
        if (args[0].isBlank() || expected <= 0) {
            throw new IllegalArgumentException("Test ID et nombre de messages invalides.");
        }
        return new TestRequest(args[0], expected);
    }

    private static Properties consumerProperties(KafkaConfig config) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "soukline-verification-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    private static List<TopicPartition> topicPartitions(KafkaConsumer<String, String> consumer, String topic) {
        List<PartitionInfo> infos = consumer.partitionsFor(topic);
        if (infos == null || infos.isEmpty()) {
            throw new IllegalStateException("Topic introuvable : " + topic);
        }
        List<TopicPartition> partitions = new ArrayList<>();
        for (PartitionInfo info : infos) {
            partitions.add(new TopicPartition(topic, info.partition()));
        }
        return partitions;
    }

    private static VerificationResult verify(KafkaConfig config, TestRequest request) {
        VerificationResult result = new VerificationResult(request.expected);
        String prefix = "SOUKLINE-LOAD|" + request.testId + "|";
        long start = System.nanoTime();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties(config))) {
            List<TopicPartition> partitions = topicPartitions(consumer, config.getLogsTopic());
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
            long deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000L;
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    validateMessage(record.value(), prefix, result);
                }
                if (reachedEnd(consumer, partitions, endOffsets)) {
                    result.finished = true;
                    break;
                }
            }
        }
        result.seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        return result;
    }

    private static void validateMessage(String value, String prefix, VerificationResult result) {
        if (value == null || !value.startsWith(prefix)) return;
        result.total++;
        String sequenceText = value.substring(prefix.length());
        try {
            int sequence = Integer.parseInt(sequenceText);
            if (sequence < 0 || sequence >= result.expected) result.invalid++;
            else if (result.received.get(sequence)) result.duplicates++;
            else result.received.set(sequence);
        } catch (NumberFormatException ex) {
            result.invalid++;
        }
    }

    private static boolean reachedEnd(KafkaConsumer<String, String> consumer,
                                      List<TopicPartition> partitions,
                                      Map<TopicPartition, Long> endOffsets) {
        for (TopicPartition partition : partitions) {
            if (consumer.position(partition) < endOffsets.get(partition)) return false;
        }
        return true;
    }

    private static void printResults(VerificationResult result) {
        int unique = result.received.cardinality();
        System.out.println("=== RESULTATS CONSUMER ===");
        System.out.println("Messages attendus : " + result.expected);
        System.out.println("Messages correspondants : " + result.total);
        System.out.println("Messages uniques : " + unique);
        System.out.println("Messages manquants : " + (result.expected - unique));
        System.out.println("Doublons : " + result.duplicates);
        System.out.println("Messages invalides : " + result.invalid);
        System.out.println("Lecture terminee : " + result.finished);
        System.out.printf("Duree totale : %.3f s%n", result.seconds);
        System.out.printf("Debit apparent : %.2f msg/s%n", unique / result.seconds);
        System.out.println(result.passed() ? "PASS : verification complete." : "FAIL : verification incomplete.");
    }

    private static final class TestRequest {
        final String testId;
        final int expected;
        TestRequest(String testId, int expected) {
            this.testId = testId;
            this.expected = expected;
        }
    }

    private static final class VerificationResult {
        final int expected;
        final BitSet received;
        int total;
        int duplicates;
        int invalid;
        boolean finished;
        double seconds;

        VerificationResult(int expected) {
            this.expected = expected;
            this.received = new BitSet(expected);
        }

        boolean passed() {
            return finished && received.cardinality() == expected
                && total == expected && duplicates == 0 && invalid == 0;
        }
    }
}
