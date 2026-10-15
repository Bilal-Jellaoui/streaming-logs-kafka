package logs.collector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogTailerTest {

    @TempDir
    Path dir;

    private static void append(Path f, String text) throws IOException {
        Files.writeString(f, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Test
    void ignoreLeContenuExistantParDefaut() throws IOException {
        Path f = dir.resolve("access.log");
        append(f, "ancienne ligne\n");
        LogTailer t = new LogTailer(f, false);

        assertTrue(t.poll().isEmpty());
        append(f, "nouvelle ligne\n");
        assertEquals(List.of("nouvelle ligne"), t.poll());
    }

    @Test
    void litToutDepuisLeDebutSiDemande() throws IOException {
        Path f = dir.resolve("access.log");
        append(f, "l1\nl2\n");
        LogTailer t = new LogTailer(f, true);

        assertEquals(List.of("l1", "l2"), t.poll());
        assertTrue(t.poll().isEmpty());
    }

    @Test
    void attendLaFinDeLigneAvantDeLaRendre() throws IOException {
        Path f = dir.resolve("access.log");
        append(f, "");
        LogTailer t = new LogTailer(f, true);

        append(f, "debut de li");
        assertTrue(t.poll().isEmpty());
        append(f, "gne\nsuite");
        assertEquals(List.of("debut de ligne"), t.poll());
        append(f, "\n");
        assertEquals(List.of("suite"), t.poll());
    }

    @Test
    void retireLeRetourChariotWindows() throws IOException {
        Path f = dir.resolve("access.log");
        append(f, "a\r\nb\r\n");
        LogTailer t = new LogTailer(f, true);

        assertEquals(List.of("a", "b"), t.poll());
    }

    @Test
    void gardeLesCaracteresAccentuesCoupesEntreDeuxLectures() throws IOException {
        Path f = dir.resolve("access.log");
        append(f, "");
        LogTailer t = new LogTailer(f, true);

        byte[] bytes = "é\n".getBytes(StandardCharsets.UTF_8);   // 'é' = 2 octets
        Files.write(f, new byte[] {bytes[0]}, StandardOpenOption.APPEND);
        assertTrue(t.poll().isEmpty());
        Files.write(f, new byte[] {bytes[1], bytes[2]}, StandardOpenOption.APPEND);
        assertEquals(List.of("é"), t.poll());
    }

    @Test
    void repartDuDebutSiLeFichierEstTronque() throws IOException {
        Path f = dir.resolve("access.log");
        append(f, "ligne longue numero un\nligne longue numero deux\n");
        LogTailer t = new LogTailer(f, true);
        assertEquals(2, t.poll().size());

        Files.writeString(f, "x\n", StandardCharsets.UTF_8);   // le fichier est vidé puis réécrit, plus court
        assertEquals(List.of("x"), t.poll());
    }

    @Test
    void attendQueLeFichierApparaisse() throws IOException {
        Path f = dir.resolve("pas-encore.log");
        LogTailer t = new LogTailer(f, false);

        assertTrue(t.poll().isEmpty());          // pas d'exception : le site n'a simplement pas démarré
        append(f, "premiere\n");
        assertEquals(List.of("premiere"), t.poll());
    }

    @Test
    void relitDepuisLeDebutApresUneRotation() throws IOException {
        Path f = dir.resolve("access.log");
        append(f, "avant rotation\n");
        LogTailer t = new LogTailer(f, true);
        assertEquals(List.of("avant rotation"), t.poll());

        Files.delete(f);                          // rotation : l'ancien fichier disparaît
        assertTrue(t.poll().isEmpty());
        append(f, "apres rotation, ligne plus longue que l'ancienne\n");
        assertEquals(List.of("apres rotation, ligne plus longue que l'ancienne"), t.poll());
    }
}
