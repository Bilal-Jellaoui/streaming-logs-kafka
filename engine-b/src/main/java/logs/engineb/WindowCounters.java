package logs.engineb;

import logs.common.LogEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Les compteurs du Moteur B, partagés par TOUS les workers (threads).
 *
 * Deux {@code ConcurrentHashMap<String, AtomicLong>} :
 *   - requests    : nombre de requêtes
 *   - errors5xx   : nombre d'erreurs 5xx
 * La clé est "débutDeFenêtre|url" (ex : "1792016400000|/checkout").
 *
 * Pourquoi ces deux outils ?
 *   - ConcurrentHashMap : plusieurs threads peuvent l'utiliser en même temps sans la casser.
 *   - AtomicLong : "+1" est atomique, donc deux threads qui comptent la même URL au même
 *     instant ne perdent jamais un +1 (avec un simple long, si : c'est la "race condition").
 */
public final class WindowCounters {

    /** Résultat d'une URL dans une fenêtre : ce qu'on publie dans "stats-b". */
    public record Snapshot(long windowStart, String url, long requests, long errors5xx) {

        /** Même format JSON que le Moteur A (seul "engine" change). */
        public String toJson() {
            String u = url.replace("\\", "\\\\").replace("\"", "\\\"");
            return "{\"engine\":\"B\",\"windowStart\":" + windowStart
                 + ",\"url\":\"" + u + "\",\"requests\":" + requests
                 + ",\"errors5xx\":" + errors5xx + "}";
        }
    }

    private final long windowMs;
    private final ConcurrentHashMap<String, AtomicLong> requests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> errors5xx = new ConcurrentHashMap<>();
    /** Les clés modifiées depuis la dernière publication (on ne republie que celles-là). */
    private final Set<String> updated = ConcurrentHashMap.newKeySet();
    /** La fenêtre la plus récente vue : sert à oublier les très vieilles fenêtres. */
    private final AtomicLong maxWindowStart = new AtomicLong(Long.MIN_VALUE);

    public WindowCounters(int windowSeconds) {
        this.windowMs = windowSeconds * 1000L;
    }

    private static String key(long windowStart, String url) {
        return windowStart + "|" + url;
    }

    private static long windowOf(String key) {
        return Long.parseLong(key.substring(0, key.indexOf('|')));
    }

    /** Compte une requête (appelée en parallèle par tous les workers). */
    public void record(LogEntry e) {
        long windowStart = Math.floorDiv(e.timestamp.toEpochMilli(), windowMs) * windowMs;
        String k = key(windowStart, e.url);
        requests.computeIfAbsent(k, x -> new AtomicLong()).incrementAndGet();
        if (e.is5xx()) {
            errors5xx.computeIfAbsent(k, x -> new AtomicLong()).incrementAndGet();
        }
        updated.add(k);
        maxWindowStart.accumulateAndGet(windowStart, Math::max);
    }

    /**
     * Retourne les totaux à jour de tout ce qui a changé depuis le dernier appel
     * (comme le Moteur A, on republie le TOTAL, pas la différence).
     */
    public List<Snapshot> drainUpdates() {
        List<Snapshot> out = new ArrayList<>();
        for (String k : updated) {
            if (!updated.remove(k)) {
                continue;
            }
            AtomicLong r = requests.get(k);
            AtomicLong er = errors5xx.get(k);
            out.add(new Snapshot(windowOf(k), k.substring(k.indexOf('|') + 1),
                                 r == null ? 0 : r.get(), er == null ? 0 : er.get()));
        }
        out.sort(Comparator.comparingLong(Snapshot::windowStart).thenComparing(Snapshot::url));
        return out;
    }

    /** Oublie les fenêtres plus vieilles que {@code keepWindows} fenêtres avant la plus récente. */
    public void purgeKeeping(int keepWindows) {
        long max = maxWindowStart.get();
        if (max == Long.MIN_VALUE) {
            return;
        }
        long min = max - keepWindows * windowMs;
        requests.keySet().removeIf(k -> windowOf(k) < min);
        errors5xx.keySet().removeIf(k -> windowOf(k) < min);
        updated.removeIf(k -> windowOf(k) < min);
    }

    /** Nombre total de requêtes comptées (utile pour les tests). */
    public long totalRequests() {
        long sum = 0;
        for (AtomicLong a : requests.values()) {
            sum += a.get();
        }
        return sum;
    }
}
