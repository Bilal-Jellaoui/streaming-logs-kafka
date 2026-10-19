package logs.benchmark;

import logs.common.LogEntry;
import logs.common.LogParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * LA REFERENCE : recompte un access.log en séquentiel, par (fenêtre, URL).
 *
 * C'est la "bonne réponse" à laquelle on compare les moteurs. Elle utilise le MÊME parseur
 * que les moteurs (logs.common.LogParser) : on teste donc le comptage et le fenêtrage,
 * pas une deuxième interprétation du format des logs.
 */
public final class ReferenceCounter {

    /** Une case du tableau de résultats : une fenêtre de temps + une URL. */
    public record Key(long windowStart, String url) implements Comparable<Key> {
        @Override
        public int compareTo(Key o) {
            int c = Long.compare(windowStart, o.windowStart);
            return c != 0 ? c : url.compareTo(o.url);
        }
    }

    /** Les deux compteurs d'une case. */
    public record Cell(long requests, long errors5xx) {
        public static final Cell ZERO = new Cell(0, 0);

        public Cell plus(Cell o) {
            return new Cell(requests + o.requests, errors5xx + o.errors5xx);
        }
    }

    /** Résultat du recomptage. */
    public record Reference(Map<Key, Cell> cells, long lines, long valid, long invalid) {
        public long errors5xx() {
            return cells.values().stream().mapToLong(Cell::errors5xx).sum();
        }
    }

    private ReferenceCounter() { }

    /** Début de fenêtre : multiple de la taille de fenêtre (comme Kafka Streams, aligné sur l'époque Unix). */
    public static long windowStart(long epochMs, long windowMs) {
        return epochMs - Math.floorMod(epochMs, windowMs);
    }

    public static Reference count(Iterable<String> lines, int windowSeconds) {
        long windowMs = windowSeconds * 1000L;
        Map<Key, Cell> cells = new TreeMap<>();
        long total = 0, valid = 0, invalid = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;               // une ligne vide n'est pas une requête
            }
            total++;
            LogEntry e = LogParser.parse(line);
            if (e == null) {
                invalid++;
                continue;
            }
            valid++;
            Key k = new Key(windowStart(e.timestamp.toEpochMilli(), windowMs), e.url);
            cells.merge(k, new Cell(1, e.is5xx() ? 1 : 0), Cell::plus);
        }
        return new Reference(cells, total, valid, invalid);
    }

    public static Reference countFile(Path accessLog, int windowSeconds) throws IOException {
        return count(readLines(accessLog), windowSeconds);
    }

    /** Lit un fichier texte sans planter sur un octet bizarre (il est remplacé au lieu de lever une erreur). */
    public static List<String> readLines(Path file) throws IOException {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.add(line);
            }
        }
        return out;
    }
}
