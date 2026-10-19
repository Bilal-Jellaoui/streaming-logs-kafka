package logs.benchmark;

import logs.benchmark.ReferenceCounter.Cell;
import logs.benchmark.ReferenceCounter.Key;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ce qu'UN moteur a publié, remis en forme.
 *
 * Un moteur (le moteur A surtout) republie une case à chaque mise à jour, avec le TOTAL à jour :
 * ce n'est pas un doublon, c'est une mise à jour. On garde donc la DERNIÈRE valeur de chaque case.
 */
public final class EngineResult {

    public final String expectedEngine;
    public final Map<Key, Cell> cells = new TreeMap<>();   // dernière valeur de chaque (fenêtre, URL)
    public long messages;       // messages lus
    public long unreadable;     // lignes qui ne sont pas du JSON attendu
    public long inconsistent;   // compteurs négatifs, erreurs > requêtes, URL vide
    public long wrongEngine;    // champ "engine" différent de celui attendu
    public long decreasing;     // un compteur qui BAISSE d'une mise à jour à l'autre

    private EngineResult(String expectedEngine) {
        this.expectedEngine = expectedEngine;
    }

    public static EngineResult fromLines(Collection<String> lines, String expectedEngine) {
        EngineResult r = new EngineResult(expectedEngine);
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            StatsMessage m = StatsMessage.parse(line);
            if (m == null) {
                r.unreadable++;
                continue;
            }
            r.messages++;
            if (!m.engine().equals(expectedEngine)) {
                r.wrongEngine++;
            }
            if (!m.isConsistent()) {
                r.inconsistent++;
                continue;
            }
            Key key = new Key(m.windowStart(), m.url());
            Cell previous = r.cells.get(key);
            if (previous != null && (m.requests() < previous.requests() || m.errors5xx() < previous.errors5xx())) {
                r.decreasing++;
            }
            r.cells.put(key, new Cell(m.requests(), m.errors5xx()));
        }
        return r;
    }

    /** Vrai si les messages eux-mêmes sont propres (indépendamment des valeurs comparées à la référence). */
    public boolean healthy() {
        return unreadable == 0 && inconsistent == 0 && wrongEngine == 0 && decreasing == 0;
    }

    public String healthSummary() {
        return "messages=" + messages + ", illisibles=" + unreadable + ", incohérents=" + inconsistent
             + ", mauvais engine=" + wrongEngine + ", compteurs décroissants=" + decreasing;
    }
}
