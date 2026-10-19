package logs.benchmark;

import logs.benchmark.Comparison.Mode;
import logs.benchmark.Comparison.Row;
import logs.benchmark.Comparison.Verdict;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Vérificateur : recompte access.log en séquentiel (LogParser partagé) et compare
 * CHAQUE moteur, séparément, à cette référence. Compare aussi A et B entre eux.
 *
 * ATTENTION, base de temps : le collecteur publie les lignes SANS horodatage explicite, donc Kafka
 * Streams fenêtre sur l'heure d'ENVOI du message, pas sur l'heure écrite dans la ligne de log.
 * Une requête proche d'une frontière de fenêtre peut donc tomber dans la fenêtre voisine.
 * C'est pourquoi on calcule toujours les DEUX niveaux :
 *   - totaux par URL (TOTALS)  : doivent être exacts, quelle que soit la base de temps ;
 *   - cases (fenêtre, URL) (WINDOWS) : exactes seulement si moteur et référence utilisent la même heure.
 * L'option --mode choisit le niveau qui décide du verdict ; l'autre est affiché à titre indicatif.
 *
 * Usage :
 *   StatsVerifier <access.log> <stats-a> [<stats-b>|-] [--window 5] [--mode totals|windows]
 *                 [--csv results/verification.csv] [--bootstrap host:port,...]
 *   où <stats-a> est "kafka:stats-a" (lecture directe du topic) ou un fichier (un JSON par ligne).
 *   Mettre "-" à la place de <stats-b> tant que le moteur B n'existe pas.
 */
public final class StatsVerifier {

    public record Report(ReferenceCounter.Reference reference, EngineResult a, EngineResult b,
                         List<Verdict> verdicts, List<Verdict> info, boolean ok) { }

    private StatsVerifier() { }

    /** statsA ou statsB peut être null (moteur absent), mais pas les deux. */
    public static Report verify(List<String> logLines, List<String> statsA, List<String> statsB,
                                int windowSeconds, Mode mode) {
        if (statsA == null && statsB == null) {
            throw new IllegalArgumentException("Il faut au moins un moteur à vérifier.");
        }
        ReferenceCounter.Reference ref = ReferenceCounter.count(logLines, windowSeconds);
        EngineResult a = statsA == null ? null : EngineResult.fromLines(statsA, "A");
        EngineResult b = statsB == null ? null : EngineResult.fromLines(statsB, "B");
        Mode other = mode == Mode.TOTALS ? Mode.WINDOWS : Mode.TOTALS;

        List<Verdict> verdicts = new ArrayList<>();
        List<Verdict> info = new ArrayList<>();
        boolean ok = true;

        if (a != null) {
            ok &= a.healthy();
            ok &= add(verdicts, info, "référence vs moteur A", ref.cells(), a.cells, mode, other);
        }
        if (b != null) {
            ok &= b.healthy();
            ok &= add(verdicts, info, "référence vs moteur B", ref.cells(), b.cells, mode, other);
        }
        if (a != null && b != null) {
            ok &= add(verdicts, info, "moteur A vs moteur B", a.cells, b.cells, mode, other);
        }
        return new Report(ref, a, b, verdicts, info, ok);
    }

    private static boolean add(List<Verdict> verdicts, List<Verdict> info, String label,
                               java.util.Map<ReferenceCounter.Key, ReferenceCounter.Cell> expected,
                               java.util.Map<ReferenceCounter.Key, ReferenceCounter.Cell> actual,
                               Mode mode, Mode other) {
        Verdict v = Comparison.compare(label, expected, actual, mode);
        verdicts.add(v);
        info.add(Comparison.compare(label, expected, actual, other));
        return v.ok();
    }

    // ------------------------------------------------------------------ sortie

    public static void writeCsv(Report report, Path csv) throws IOException {
        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            w.println("comparison,mode,check,expected_requests,actual_requests,expected_5xx,actual_5xx,status");
            for (List<Verdict> list : List.of(report.verdicts(), report.info())) {
                for (Verdict v : list) {
                    for (Row r : v.rows()) {
                        w.println(String.join(",", quote(v.label()), v.mode().name(), quote(r.check()),
                                String.valueOf(r.expected().requests()), String.valueOf(r.actual().requests()),
                                String.valueOf(r.expected().errors5xx()), String.valueOf(r.actual().errors5xx()),
                                r.ok() ? "OK" : "FAIL"));
                    }
                }
            }
        }
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static void print(Report r, Mode mode) {
        System.out.println("=== VERIFICATION (verdict : mode " + mode + ") ===");
        System.out.println("Référence : " + r.reference().lines() + " lignes, " + r.reference().valid()
                + " valides, " + r.reference().invalid() + " ignorées, " + r.reference().errors5xx() + " erreurs 5xx");
        if (r.a() != null) {
            System.out.println("Moteur A  : " + r.a().healthSummary());
        }
        if (r.b() != null) {
            System.out.println("Moteur B  : " + r.b().healthSummary());
        }
        for (Verdict v : r.verdicts()) {
            printVerdict(v, "");
        }
        System.out.println("--- à titre indicatif (autre niveau) ---");
        for (Verdict v : r.info()) {
            printVerdict(v, "  ");
        }
        boolean totalsOkWindowsKo = false;
        for (int i = 0; i < r.verdicts().size(); i++) {
            Verdict main = r.verdicts().get(i);
            Verdict other = r.info().get(i);
            Verdict totals = main.mode() == Mode.TOTALS ? main : other;
            Verdict windows = main.mode() == Mode.WINDOWS ? main : other;
            if (totals.ok() && !windows.ok()) {
                totalsOkWindowsKo = true;
            }
        }
        if (totalsOkWindowsKo) {
            System.out.println("NOTE : les totaux sont exacts mais pas les fenêtres : probable décalage de base de temps"
                    + " (heure d'envoi Kafka contre heure du log), pas une erreur de comptage.");
        }
        System.out.println(r.ok() ? "RESULTAT : CONFORME" : "RESULTAT : ECART DETECTE");
    }

    private static void printVerdict(Verdict v, String indent) {
        System.out.println(indent + (v.ok() ? "[OK]    " : "[ECART] ") + v.label() + " (" + v.mode() + ") : "
                + v.keysChecked() + " cases comparées, " + v.mismatches() + " écart(s)");
        int shown = 0;
        for (Row row : v.rows()) {
            if (row.ok() || shown >= 15) {
                continue;
            }
            shown++;
            System.out.println(indent + "    " + row.check() + " : attendu " + row.expected().requests() + "/"
                    + row.expected().errors5xx() + " (req/5xx), obtenu " + row.actual().requests() + "/"
                    + row.actual().errors5xx());
        }
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        List<String> pos = new ArrayList<>();
        int window = 5;
        Mode mode = Mode.TOTALS;
        Path csv = Path.of("results/verification.csv");
        String bootstrap = StatsSource.DEFAULT_BOOTSTRAP;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--window" -> window = Integer.parseInt(args[++i]);
                case "--mode" -> mode = Mode.valueOf(args[++i].toUpperCase(java.util.Locale.ROOT));
                case "--csv" -> csv = Path.of(args[++i]);
                case "--bootstrap" -> bootstrap = args[++i];
                default -> pos.add(args[i]);
            }
        }
        if (pos.size() < 2) {
            System.err.println("Usage: StatsVerifier <access.log> <stats-a|kafka:stats-a> [<stats-b>|-]"
                    + " [--window 5] [--mode totals|windows] [--csv results/verification.csv] [--bootstrap h:p,...]");
            System.exit(2);
        }
        List<String> logLines = ReferenceCounter.readLines(Path.of(pos.get(0)));
        List<String> a = pos.get(1).equals("-") ? null : StatsSource.read(pos.get(1), bootstrap);
        List<String> b = pos.size() < 3 || pos.get(2).equals("-") ? null : StatsSource.read(pos.get(2), bootstrap);

        Report report = verify(logLines, a, b, window, mode);
        writeCsv(report, csv);
        print(report, mode);
        System.out.println("CSV écrit dans " + csv);
        System.exit(report.ok() ? 0 : 1);
    }
}
