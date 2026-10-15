package logs.collector;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Lit un fichier de log "en continu", comme {@code tail -f} : à chaque appel de {@link #poll()},
 * on récupère les lignes complètes écrites depuis le dernier appel.
 *
 * Cas gérés :
 *  - ligne écrite à moitié (pas encore de retour à la ligne) : on attend la suite, rien n'est perdu ;
 *  - fichier tronqué (vidé) ou remplacé (rotation) : on repart du début du nouveau fichier ;
 *  - fichier absent au départ (le site n'a pas encore démarré) : on attend qu'il apparaisse ;
 *  - fins de ligne Windows (\r\n) : le \r est retiré.
 *
 * Classe volontairement sans Kafka : elle se teste avec de simples fichiers temporaires.
 */
public final class LogTailer {

    private static final int MAX_BLOCK = 1 << 20; // on lit au plus 1 Mo par appel de poll()

    private final Path file;
    private final boolean fromStart;

    private boolean started;      // vrai après la première fois où le fichier a été vu
    private boolean missing;      // vrai si le fichier a disparu depuis la dernière lecture
    private Object fileKey;       // identité du fichier (null sous Windows) pour repérer une rotation
    private long position;        // nombre d'octets déjà lus
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream(); // début de ligne incomplète

    /**
     * @param file      le fichier à suivre (ex : logs/access.log)
     * @param fromStart vrai : lire aussi ce qui est déjà dans le fichier ; faux : ne lire que les nouvelles lignes
     *                  (comportement de tail -f). Après une rotation ou une troncature, on lit toujours depuis le début.
     */
    public LogTailer(Path file, boolean fromStart) {
        this.file = file;
        this.fromStart = fromStart;
    }

    /** Retourne les nouvelles lignes complètes (liste vide s'il n'y a rien de neuf). */
    public List<String> poll() throws IOException {
        List<String> lines = new ArrayList<>();

        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(file, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            missing = true;      // absent (pas encore créé, ou rotation en cours) : s'il apparaît, tout son contenu est neuf
            return lines;
        }
        long size = attrs.size();
        Object key = attrs.fileKey();

        if (!started) {
            position = (fromStart || missing) ? 0 : size;
            fileKey = key;
            started = true;
        } else if (missing || (key != null && fileKey != null && !key.equals(fileKey))) {
            reset(key);          // fichier réapparu ou remplacé : nouveau fichier, on le lit depuis le début
        } else if (size < position) {
            reset(key);          // fichier tronqué
        }
        missing = false;

        if (size <= position) {
            return lines;
        }

        int toRead = (int) Math.min(size - position, MAX_BLOCK);
        byte[] buf = new byte[toRead];
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(position);
            raf.readFully(buf);
        } catch (EOFException e) {
            reset(key);          // le fichier a été tronqué pendant la lecture : on repartira du début
            return lines;
        }
        position += toRead;

        for (byte b : buf) {
            if (b == '\n') {
                lines.add(decodePending());
            } else {
                pending.write(b);
            }
        }
        return lines;
    }

    private void reset(Object newKey) {
        position = 0;
        pending.reset();
        fileKey = newKey;
    }

    private String decodePending() {
        String s = new String(pending.toByteArray(), StandardCharsets.UTF_8);
        pending.reset();
        if (s.endsWith("\r")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
