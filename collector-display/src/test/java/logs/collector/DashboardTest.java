package logs.collector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardTest {

    private static StatsStore.Summary summary(String engine, long req, long err, StatsStore.UrlStats... urls) {
        return new StatsStore.Summary(engine, req, err, List.of(urls));
    }

    private static final AlertMonitor.State OK = new AlertMonitor.State(false, 2.0, "A", 100, false);

    @Test
    void afficheLesDeuxMoteursCoteACote() {
        StatsStore.Summary a = summary("A", 150, 3, new StatsStore.UrlStats("/checkout", 90, 3));
        StatsStore.Summary b = summary("B", 140, 2, new StatsStore.UrlStats("/checkout", 85, 2));

        String out = Dashboard.render(a, b, OK, 10.0, 1_790_000_000_000L, 15, 3);

        assertTrue(out.contains("MOTEUR A"));
        assertTrue(out.contains("MOTEUR B"));
        assertTrue(out.contains("Requetes : 150  (10.0 /s)"));   // 150 requêtes sur 15 s
        assertTrue(out.contains("Requetes : 140  (9.3 /s)"));
        assertTrue(out.contains("Erreurs 5xx : 3  (2.0 %)"));
        assertTrue(out.contains("/checkout"));
        assertTrue(out.contains("Statut : OK"));
        assertFalse(out.contains("ALERTE"));
    }

    @Test
    void afficheLAlerteQuandLeSeuilEstDepasse() {
        AlertMonitor.State alert = new AlertMonitor.State(true, 37.5, "A", 80, true);

        String out = Dashboard.render(summary("A", 80, 30), summary("B", 0, 0), alert, 10.0, 0L, 15, 3);

        assertTrue(out.contains("!!! ALERTE"));
        assertTrue(out.contains("37.5 %"));
        assertTrue(out.contains("seuil 10.0 %"));
        assertFalse(out.contains("Statut : OK"));
    }

    @Test
    void ecranVideSansDonneesNePlantePas() {
        String out = Dashboard.render(summary("A", 0, 0), summary("B", 0, 0),
                                      new AlertMonitor.State(false, 0.0, "A", 0, false), 10.0, 0L, 15, 3);

        assertTrue(out.contains("(aucune donnee)"));
        assertTrue(out.contains("Requetes : 0  (0.0 /s)"));
    }

    @Test
    void tronqueLesUrlTropLongues() {
        String longue = "/une/url/vraiment/tres/tres/longue/qui/depasse";
        String out = Dashboard.render(summary("A", 1, 0, new StatsStore.UrlStats(longue, 1, 0)),
                                      summary("B", 0, 0), OK, 10.0, 0L, 15, 3);

        assertFalse(out.contains(longue));
        assertTrue(out.contains("~"));
    }

    @Test
    void uneLigneParUrlJusquAuTopN() {
        StatsStore.Summary a = summary("A", 6, 0,
            new StatsStore.UrlStats("/1", 3, 0), new StatsStore.UrlStats("/2", 2, 0), new StatsStore.UrlStats("/3", 1, 0));

        String out = Dashboard.render(a, summary("B", 0, 0), OK, 10.0, 0L, 15, 3);

        assertTrue(out.contains("/1"));
        assertTrue(out.contains("/2"));
        assertTrue(out.contains("/3"));
    }
}
