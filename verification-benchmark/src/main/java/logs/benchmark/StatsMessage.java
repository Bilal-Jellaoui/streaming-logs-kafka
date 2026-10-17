package logs.benchmark;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un message publié par un moteur dans stats-a ou stats-b :
 * {"engine":"A","windowStart":1791383525000,"url":"/checkout","requests":3,"errors5xx":2}
 *
 * Pas de bibliothèque JSON dans le projet : le format est plat, des expressions régulières suffisent
 * (même approche que StatsJson dans collector-display). L'ordre des champs est libre.
 */
public record StatsMessage(String engine, long windowStart, String url, long requests, long errors5xx) {

    private static final Pattern ENGINE = Pattern.compile("\"engine\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern WINDOW = Pattern.compile("\"windowStart\"\\s*:\\s*(-?\\d+)");
    private static final Pattern URL = Pattern.compile("\"url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern REQUESTS = Pattern.compile("\"requests\"\\s*:\\s*(-?\\d+)");
    private static final Pattern ERRORS = Pattern.compile("\"errors5xx\"\\s*:\\s*(-?\\d+)");

    /** Retourne le message, ou null si un champ manque ou si le texte n'est pas le JSON attendu. */
    public static StatsMessage parse(String json) {
        if (json == null) {
            return null;
        }
        String engine = find(ENGINE, json);
        String window = find(WINDOW, json);
        String url = find(URL, json);
        String requests = find(REQUESTS, json);
        String errors = find(ERRORS, json);
        if (engine == null || window == null || url == null || requests == null || errors == null) {
            return null;
        }
        try {
            return new StatsMessage(engine, Long.parseLong(window), unescape(url),
                                    Long.parseLong(requests), Long.parseLong(errors));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Règles de bon sens : pas de compteur négatif, jamais plus d'erreurs que de requêtes, URL non vide. */
    public boolean isConsistent() {
        return requests >= 0 && errors5xx >= 0 && errors5xx <= requests && !url.isEmpty();
    }

    private static String find(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                sb.append(s.charAt(++i));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
