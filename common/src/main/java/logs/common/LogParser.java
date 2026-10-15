package logs.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforme une ligne de texte (format Tomcat "common") en LogEntry.
 * Les lignes mal formées donnent null : à l'appelant de les ignorer.
 */
public final class LogParser {

    // Les 6 groupes entre parenthèses :
    // 1 = ip, 2 = date, 3 = méthode (GET...), 4 = URL, 5 = code status, 6 = taille (ou "-")
    private static final Pattern LINE = Pattern.compile(
        "^(\\S+) \\S+ \\S+ \\[([^\\]]+)\\] \"(\\S+) (\\S+)(?: \\S+)?\" (\\d{3}) (\\d+|-)$");

    // /css/..., /js/..., /img/... ou un fichier .css, .js, .png, ... = ressource statique
    private static final Pattern STATIC = Pattern.compile(
        "^/(css|js|img|images|fonts|static)/.*|.*\\.(css|js|png|jpe?g|gif|svg|ico|woff2?|map)$");

    // /produits/12 (un produit précis)
    private static final Pattern PRODUIT_ID = Pattern.compile("^/produits/\\d+$");

    private static final DateTimeFormatter TS =
        DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    private LogParser() { }

    /** Retourne la LogEntry, ou null si la ligne est mal formée. */
    public static LogEntry parse(String line) {
        if (line == null) {
            return null;
        }
        Matcher m = LINE.matcher(line.trim());
        if (!m.matches()) {
            return null;
        }
        try {
            Instant ts = OffsetDateTime.parse(m.group(2), TS).toInstant();
            long bytes = m.group(6).equals("-") ? 0 : Long.parseLong(m.group(6));
            return new LogEntry(m.group(1), ts, m.group(3), normalize(m.group(4)),
                                Integer.parseInt(m.group(5)), bytes);
        } catch (DateTimeParseException | NumberFormatException e) {
            return null;
        }
    }

    /**
     * Rend les URL comparables entre elles :
     *   /recherche?q=chaise -> /recherche   (sans paramètres)
     *   /produits/12        -> /produits/{id}
     *   /css/site.css       -> /static
     *   /produits/          -> /produits    (sans "/" final)
     */
    public static String normalize(String raw) {
        String p = raw;
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        int sc = p.indexOf(';');
        if (sc >= 0) {
            p = p.substring(0, sc);   // ;SOUKLINE_SESSION=... (paramètre de session Tomcat)
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (STATIC.matcher(p).matches()) {
            return "/static";
        }
        if (PRODUIT_ID.matcher(p).matches()) {
            return "/produits/{id}";
        }
        return p;
    }
}
