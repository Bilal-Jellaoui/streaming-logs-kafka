package logs.common;

import java.time.Instant;

/**
 * Une ligne de log HTTP, déjà découpée en champs.
 * Exemple de ligne d'origine (format Tomcat "common") :
 * 127.0.0.1 - - [07/Oct/2026:14:32:05 +0000] "GET /checkout HTTP/1.1" 503 1234
 */
public final class LogEntry {
    public final String ip;
    public final Instant timestamp;
    public final String method;
    public final String url;     // URL déjà normalisée (voir LogParser.normalize)
    public final int status;
    public final long bytes;

    public LogEntry(String ip, Instant timestamp, String method, String url, int status, long bytes) {
        this.ip = ip;
        this.timestamp = timestamp;
        this.method = method;
        this.url = url;
        this.status = status;
        this.bytes = bytes;
    }

    /** Vrai si le code de statut est une erreur serveur (500 à 599). */
    public boolean is5xx() {
        return status >= 500 && status <= 599;
    }

    @Override
    public String toString() {
        return method + " " + url + " -> " + status + " @" + timestamp;
    }
}
