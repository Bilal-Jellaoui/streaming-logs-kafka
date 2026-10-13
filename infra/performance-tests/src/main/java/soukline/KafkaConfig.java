package soukline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Configuration partagee des outils de diagnostic Kafka. */
public final class KafkaConfig {
    private final Properties properties = new Properties();

    public KafkaConfig(String configPath) throws IOException {
        Path path = Path.of(configPath);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Configuration introuvable : " + path.toAbsolutePath());
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
    }

    public String getBootstrapServers() {
        return required("bootstrap.servers");
    }

    public String getLogsTopic() {
        return required("topic.logs");
    }

    public String getProducerAcks() {
        return required("producer.acks");
    }

    private String required(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Propriete manquante : " + key);
        }
        return value.trim();
    }
}
