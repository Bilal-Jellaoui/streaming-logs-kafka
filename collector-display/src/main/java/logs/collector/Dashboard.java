package logs.collector;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Met en forme le tableau de bord console : Moteur A et Moteur B côte à côte
 * (requêtes, débit, erreurs 5xx, top URLs), puis la ligne d'état de l'alerte.
 * Texte en ASCII pur pour s'afficher correctement dans n'importe quelle console Windows.
 */
public final class Dashboard {

    private static final int COL = 38;   // largeur d'une colonne (un moteur)
    private static final int URL_W = 20; // largeur du champ URL
    private static final DateTimeFormatter CLOCK =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private Dashboard() { }

    /**
     * @param periodSeconds durée couverte par les résumés (nombre de fenêtres x taille d'une fenêtre)
     */
    public static String render(StatsStore.Summary a, StatsStore.Summary b,
                                AlertMonitor.State alert, double thresholdPercent,
                                long nowMs, int periodSeconds, int topN) {
        StringBuilder sb = new StringBuilder();
        String sep = "-".repeat(COL * 2 + 3);

        sb.append("=== SoukLine : logs en direct === ").append(CLOCK.format(Instant.ofEpochMilli(nowMs)))
          .append("  (derniere periode : ").append(periodSeconds).append(" s)\n");
        sb.append(sep).append('\n');
        sb.append(row("MOTEUR A (Kafka Streams)", "MOTEUR B (concurrence manuelle)"));
        sb.append(sep).append('\n');
        sb.append(row(totals(a, periodSeconds), totals(b, periodSeconds)));
        sb.append(row(errors(a), errors(b)));
        sb.append(sep).append('\n');
        sb.append(row(header(), header()));

        for (int i = 0; i < topN; i++) {
            sb.append(row(urlLine(a.topUrls(), i, a.requests() == 0), urlLine(b.topUrls(), i, b.requests() == 0)));
        }
        sb.append(sep).append('\n');
        sb.append(alertLine(alert, thresholdPercent)).append('\n');
        return sb.toString();
    }

    private static String totals(StatsStore.Summary s, int periodSeconds) {
        double perSecond = periodSeconds <= 0 ? 0.0 : (double) s.requests() / periodSeconds;
        return String.format(Locale.ROOT, "Requetes : %d  (%.1f /s)", s.requests(), perSecond);
    }

    private static String errors(StatsStore.Summary s) {
        return String.format(Locale.ROOT, "Erreurs 5xx : %d  (%.1f %%)", s.errors5xx(), s.errorRate() * 100.0);
    }

    private static String header() {
        return String.format(Locale.ROOT, "%-" + URL_W + "s %7s %6s", "URL", "req", "5xx");
    }

    private static String urlLine(List<StatsStore.UrlStats> urls, int index, boolean noData) {
        if (index >= urls.size()) {
            return (index == 0 && noData) ? "(aucune donnee)" : "";
        }
        StatsStore.UrlStats u = urls.get(index);
        String url = u.url().length() > URL_W ? u.url().substring(0, URL_W - 1) + "~" : u.url();
        return String.format(Locale.ROOT, "%-" + URL_W + "s %7d %6d", url, u.requests(), u.errors5xx());
    }

    private static String alertLine(AlertMonitor.State alert, double thresholdPercent) {
        if (alert.alerting()) {
            return String.format(Locale.ROOT,
                "!!! ALERTE : erreurs 5xx = %.1f %% (seuil %.1f %%), moteur %s, %d requetes",
                alert.ratePercent(), thresholdPercent, alert.source(), alert.requests());
        }
        return String.format(Locale.ROOT,
            "Statut : OK (erreurs 5xx = %.1f %%, seuil %.1f %%)", alert.ratePercent(), thresholdPercent);
    }

    private static String row(String left, String right) {
        return pad(left) + " | " + pad(right) + "\n";
    }

    private static String pad(String s) {
        if (s.length() >= COL) {
            return s.substring(0, COL);
        }
        return s + " ".repeat(COL - s.length());
    }
}
