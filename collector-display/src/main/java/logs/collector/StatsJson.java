package logs.collector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lit le JSON commun aux Moteurs A et B : {engine, windowStart, url, requests, errors5xx}.
 * Pas de bibliothèque JSON dans le projet : le format est plat, quelques expressions régulières suffisent.
 * L'ordre des champs n'a pas d'importance. Un message mal formé donne null (à l'appelant de l'ignorer).
 */
public final class StatsJson {

    private static final Pattern ENGINE = Pattern.compile("\"engine\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern WINDOW = Pattern.compile("\"windowStart\"\\s*:\\s*(-?\\d+)");
    private static final Pattern URL = Pattern.compile("\"url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern REQUESTS = Pattern.compile("\"requests\"\\s*:\\s*(\\d+)");
    private static final Pattern ERRORS = Pattern.compile("\"errors5xx\"\\s*:\\s*(\\d+)");

    private StatsJson() { }

    /** Retourne le StatEvent, ou null si un champ manque ou si le texte n'est pas du JSON attendu. */
    public static StatEvent parse(String json) {
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
            return new StatEvent(engine, Long.parseLong(window), unescape(url),
                                 Long.parseLong(requests), Long.parseLong(errors));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String find(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** Annule l'échappement JSON de base : \" devient " et \\ devient \ (c'est tout ce que produit le Moteur A). */
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
