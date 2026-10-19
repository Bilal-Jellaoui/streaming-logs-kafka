package logs.benchmark;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * D'où viennent les résultats d'un moteur ?
 *   "kafka:stats-a"  -> lus directement dans le topic Kafka
 *   "stats-a.txt"    -> lus dans un fichier (un JSON par ligne, par exemple exporté avec export-stats.ps1)
 */
public final class StatsSource {

    public static final String DEFAULT_BOOTSTRAP = "localhost:9098,localhost:9094,localhost:9096";

    private StatsSource() { }

    public static List<String> read(String spec, String bootstrap) throws IOException {
        if (spec.startsWith("kafka:")) {
            return KafkaStatsReader.readAll(bootstrap, spec.substring("kafka:".length()), Duration.ofSeconds(60));
        }
        return ReferenceCounter.readLines(Path.of(spec));
    }
}
