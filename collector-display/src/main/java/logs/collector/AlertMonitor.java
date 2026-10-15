package logs.collector;

/**
 * Alerte : se déclenche quand la part d'erreurs 5xx dépasse un seuil.
 *
 * On se base sur le Moteur A ; s'il n'a rien reçu (arrêté ou en panne), on se rabat sur le Moteur B.
 * Pour éviter les fausses alertes quand il y a très peu de trafic (1 erreur sur 2 requêtes = 50 %),
 * il faut un minimum de requêtes dans la période observée.
 */
public final class AlertMonitor {

    /**
     * @param alerting      vrai si le seuil est dépassé en ce moment
     * @param ratePercent   part de 5xx en pourcent (0 à 100)
     * @param source        moteur utilisé pour le calcul ("A" ou "B")
     * @param requests      nombre de requêtes sur lesquelles le taux est calculé
     * @param justRaised    vrai uniquement au moment où l'alerte passe de "OK" à "ALERTE"
     */
    public record State(boolean alerting, double ratePercent, String source, long requests, boolean justRaised) { }

    private final double thresholdPercent;
    private final long minRequests;
    private boolean alerting;

    public AlertMonitor(double thresholdPercent, long minRequests) {
        this.thresholdPercent = thresholdPercent;
        this.minRequests = minRequests;
    }

    public double thresholdPercent() {
        return thresholdPercent;
    }

    public State evaluate(StatsStore.Summary a, StatsStore.Summary b) {
        StatsStore.Summary source = a.requests() > 0 ? a : b;
        double rate = source.errorRate() * 100.0;
        boolean now = source.requests() >= minRequests && rate > thresholdPercent;
        boolean raised = now && !alerting;
        alerting = now;
        return new State(now, rate, source.engine(), source.requests(), raised);
    }
}
