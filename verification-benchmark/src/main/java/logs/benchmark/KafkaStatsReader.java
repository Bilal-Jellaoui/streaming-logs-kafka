package logs.benchmark;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Lit TOUT le contenu actuel d'un topic (stats-a ou stats-b), du début jusqu'à la fin telle qu'elle
 * est au moment de l'appel, puis s'arrête. Ne modifie rien dans Kafka (aucun commit d'offset).
 */
final class KafkaStatsReader {

    private KafkaStatsReader() { }

    static List<String> readAll(String bootstrap, String topic, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "verifier-" + UUID.randomUUID());   // groupe jetable
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        List<String> values = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<PartitionInfo> infos = consumer.partitionsFor(topic);
            if (infos == null || infos.isEmpty()) {
                throw new IllegalStateException("Topic introuvable : " + topic);
            }
            List<TopicPartition> partitions = new ArrayList<>();
            for (PartitionInfo info : infos) {
                partitions.add(new TopicPartition(topic, info.partition()));
            }
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);   // "photo" de la fin du topic

            long deadline = System.nanoTime() + timeout.toNanos();
            while (!reachedEnd(consumer, partitions, end)) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("Lecture de " + topic + " trop longue (> " + timeout.toSeconds() + " s)");
                }
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    if (r.value() != null) {
                        values.add(r.value());
                    }
                }
            }
        }
        return values;
    }

    private static boolean reachedEnd(KafkaConsumer<String, String> c, List<TopicPartition> parts,
                                      Map<TopicPartition, Long> end) {
        for (TopicPartition tp : parts) {
            if (c.position(tp) < end.get(tp)) {
                return false;
            }
        }
        return true;
    }
}
