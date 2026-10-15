package logs.collector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mémoire de l'affichage : garde les derniers résultats reçus de chaque moteur,
 * rangés par fenêtre de temps puis par URL.
 *
 * Hypothèse : un moteur republie une URL d'une fenêtre à chaque mise à jour, avec le TOTAL
 * à jour (c'est ce que fait le Moteur A). On garde donc seulement le dernier message reçu
 * pour chaque (moteur, fenêtre, URL), on ne les additionne pas.
 *
 * Thread-safe : un thread alimente le store (consumer Kafka), un autre le lit (affichage).
 */
public final class StatsStore {

    /** Compteurs d'une URL (ou, dans un Summary, total sur plusieurs fenêtres). */
    public record UrlStats(String url, long requests, long errors5xx) { }

    /** Vue résumée d'un moteur sur les dernières fenêtres terminées. */
    public record Summary(String engine, long requests, long errors5xx, List<UrlStats> topUrls) {
        /** Part des requêtes en erreur 5xx, entre 0.0 et 1.0 (0 s'il n'y a aucune requête). */
        public double errorRate() {
            return requests == 0 ? 0.0 : (double) errors5xx / requests;
        }
    }

    private final long windowMs;
    private final long keepMs;
    // moteur -> (début de fenêtre -> (url -> {requêtes, erreurs 5xx}))
    private final Map<String, TreeMap<Long, Map<String, long[]>>> data = new HashMap<>();

    /**
     * @param windowSeconds taille des fenêtres des moteurs (5 par défaut, comme -Dwindow.seconds du Moteur A)
     * @param keepWindows   nombre de fenêtres gardées en mémoire (les plus anciennes sont oubliées)
     */
    public StatsStore(int windowSeconds, int keepWindows) {
        this.windowMs = windowSeconds * 1000L;
        this.keepMs = keepWindows * windowMs;
    }

    public synchronized void apply(StatEvent e) {
        TreeMap<Long, Map<String, long[]>> windows = data.computeIfAbsent(e.engine(), k -> new TreeMap<>());
        windows.computeIfAbsent(e.windowStart(), k -> new HashMap<>())
               .put(e.url(), new long[] {e.requests(), e.errors5xx()});
        windows.headMap(windows.lastKey() - keepMs).clear();   // on oublie les fenêtres trop anciennes
    }

    /**
     * Résume un moteur sur les {@code windows} dernières fenêtres TERMINÉES (la fenêtre en cours,
     * encore incomplète, est exclue pour ne pas fausser les débits).
     *
     * @param nowMs l'heure actuelle en ms (passée en paramètre pour pouvoir tester)
     * @param topN  nombre d'URL à garder dans le classement
     */
    public synchronized Summary summary(String engine, long nowMs, int windows, int topN) {
        long currentStart = Math.floorDiv(nowMs, windowMs) * windowMs;
        long from = currentStart - windows * windowMs;
        long to = currentStart - windowMs;

        Map<String, long[]> perUrl = new HashMap<>();
        TreeMap<Long, Map<String, long[]>> all = data.get(engine);
        if (all != null) {
            for (Map<String, long[]> window : all.subMap(from, true, to, true).values()) {
                for (Map.Entry<String, long[]> en : window.entrySet()) {
                    long[] acc = perUrl.computeIfAbsent(en.getKey(), k -> new long[2]);
                    acc[0] += en.getValue()[0];
                    acc[1] += en.getValue()[1];
                }
            }
        }

        long requests = 0;
        long errors = 0;
        List<UrlStats> urls = new ArrayList<>();
        for (Map.Entry<String, long[]> en : perUrl.entrySet()) {
            requests += en.getValue()[0];
            errors += en.getValue()[1];
            urls.add(new UrlStats(en.getKey(), en.getValue()[0], en.getValue()[1]));
        }
        urls.sort(Comparator.comparingLong(UrlStats::requests).reversed().thenComparing(UrlStats::url));
        if (urls.size() > topN) {
            urls = new ArrayList<>(urls.subList(0, topN));
        }
        return new Summary(engine, requests, errors, urls);
    }
}
