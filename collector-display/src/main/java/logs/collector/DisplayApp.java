package logs.collector;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Affichage console en direct : lit "stats-a" et "stats-b", et montre les deux moteurs côte à côte
 * (top URLs, requêtes/s, erreurs 5xx), avec une alerte si le taux de 5xx dépasse un seuil.
 *
 * Réglages possibles au lancement (sinon valeurs par défaut) :
 *   -Dkafka.bootstrap=localhost:9098,localhost:9094,localhost:9096
 *   -Dwindow.seconds=5        (doit être égal à celui des moteurs)
 *   -Ddisplay.windows=3       (nombre de fenêtres terminées affichées : 3 x 5 s = 15 s)
 *   -Ddisplay.top=8           (nombre d'URL dans le classement)
 *   -Drefresh.seconds=2       (fréquence d'affichage)
 *   -Dalert.threshold=10.0    (seuil d'alerte, en % de requêtes 5xx)
 *   -Dalert.minRequests=10    (pas d'alerte en dessous de ce nombre de requêtes)
 *   -Ddisplay.clear=true      (effacer l'écran à chaque affichage ; à réserver à un vrai terminal, pas à la console IntelliJ)
 */
public class DisplayApp {

    static final String BOOTSTRAP = System.getProperty("kafka.bootstrap", "localhost:9098,localhost:9094,localhost:9096");
    static final String TOPIC_A = System.getProperty("topic.stats.a", "stats-a");
    static final String TOPIC_B = System.getProperty("topic.stats.b", "stats-b");
    static final int WINDOW_SECONDS = Integer.getInteger("window.seconds", 5);
    static final int WINDOWS = Integer.getInteger("display.windows", 3);
    static final int TOP = Integer.getInteger("display.top", 8);
    static final int REFRESH_SECONDS = Integer.getInteger("refresh.seconds", 2);
    static final double ALERT_THRESHOLD = Double.parseDouble(System.getProperty("alert.threshold", "10.0"));
    static final long ALERT_MIN_REQUESTS = Long.getLong("alert.minRequests", 10L);
    static final boolean CLEAR = Boolean.getBoolean("display.clear");

    static Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "display-" + UUID.randomUUID());   // groupe jetable : on veut le direct
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");                // pas besoin de rejouer l'historique
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    public static void main(String[] args) {
        StatsStore store = new StatsStore(WINDOW_SECONDS, Math.max(WINDOWS * 4, 60));
        AlertMonitor monitor = new AlertMonitor(ALERT_THRESHOLD, ALERT_MIN_REQUESTS);
        int periodSeconds = WINDOWS * WINDOW_SECONDS;

        ScheduledExecutorService renderer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "affichage");
            t.setDaemon(true);
            return t;
        });
        renderer.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                StatsStore.Summary a = store.summary("A", now, WINDOWS, TOP);
                StatsStore.Summary b = store.summary("B", now, WINDOWS, TOP);
                AlertMonitor.State alert = monitor.evaluate(a, b);
                String screen = Dashboard.render(a, b, alert, monitor.thresholdPercent(), now, periodSeconds, TOP);
                if (CLEAR) {
                    System.out.print("\u001b[H\u001b[2J");   // curseur en haut à gauche + effacer l'écran
                }
                if (alert.justRaised()) {
                    System.out.print("\u0007");              // bip du terminal
                }
                System.out.println(screen);
            } catch (RuntimeException e) {
                System.err.println("[Affichage] erreur : " + e);   // un problème d'affichage ne doit pas tout arrêter
            }
        }, REFRESH_SECONDS, REFRESH_SECONDS, TimeUnit.SECONDS);

        System.out.println("[Affichage] démarrage : " + TOPIC_A + " + " + TOPIC_B
                           + " (Kafka " + BOOTSTRAP + ", fenêtres de " + WINDOW_SECONDS + " s, seuil 5xx " + ALERT_THRESHOLD + " %)");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
            Thread mainThread = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                consumer.wakeup();   // fait sortir proprement la boucle de lecture
                try {
                    mainThread.join(3000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }));

            consumer.subscribe(List.of(TOPIC_A, TOPIC_B));
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    StatEvent event = StatsJson.parse(record.value());
                    if (event != null) {            // message mal formé : on l'ignore
                        store.apply(event);
                    }
                }
            }
        } catch (WakeupException e) {
            // arrêt demandé (Ctrl+C) : rien à faire, le consumer est fermé par le try
        } finally {
            renderer.shutdownNow();
        }
        System.out.println("[Affichage] arrêt");
    }
}
