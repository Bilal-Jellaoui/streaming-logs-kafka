package logs.collector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsStoreTest {

    // Fenêtres de 5 s. "Maintenant" = 100_000 + 2_000 : la fenêtre en cours démarre à 100_000,
    // donc les fenêtres terminées sont 95_000, 90_000, 85_000...
    private static final long NOW = 102_000L;

    private static StatEvent ev(String engine, long window, String url, long req, long err) {
        return new StatEvent(engine, window, url, req, err);
    }

    @Test
    void leDernierMessageRemplaceLePrecedentSansAdditionner() {
        StatsStore s = new StatsStore(5, 60);
        s.apply(ev("A", 95_000, "/checkout", 1, 0));
        s.apply(ev("A", 95_000, "/checkout", 3, 2));   // mise à jour : total à jour de la même fenêtre

        StatsStore.Summary sum = s.summary("A", NOW, 1, 8);
        assertEquals(3, sum.requests());
        assertEquals(2, sum.errors5xx());
    }

    @Test
    void additionneLesFenetresTermineesEtIgnoreLaFenetreEnCours() {
        StatsStore s = new StatsStore(5, 60);
        s.apply(ev("A", 90_000, "/", 10, 0));
        s.apply(ev("A", 95_000, "/", 20, 1));
        s.apply(ev("A", 100_000, "/", 500, 500));      // fenêtre en cours : incomplète, exclue

        StatsStore.Summary sum = s.summary("A", NOW, 2, 8);
        assertEquals(30, sum.requests());
        assertEquals(1, sum.errors5xx());
    }

    @Test
    void ignoreLesFenetresTropAnciennesPourLaPeriode() {
        StatsStore s = new StatsStore(5, 60);
        s.apply(ev("A", 80_000, "/vieux", 50, 0));     // hors des 2 dernières fenêtres terminées
        s.apply(ev("A", 95_000, "/recent", 5, 0));

        StatsStore.Summary sum = s.summary("A", NOW, 2, 8);
        assertEquals(5, sum.requests());
        assertEquals(1, sum.topUrls().size());
        assertEquals("/recent", sum.topUrls().get(0).url());
    }

    @Test
    void lesMoteursSontSepares() {
        StatsStore s = new StatsStore(5, 60);
        s.apply(ev("A", 95_000, "/", 10, 1));
        s.apply(ev("B", 95_000, "/", 7, 0));

        assertEquals(10, s.summary("A", NOW, 1, 8).requests());
        assertEquals(7, s.summary("B", NOW, 1, 8).requests());
        assertEquals(0, s.summary("C", NOW, 1, 8).requests());   // moteur inconnu : résumé vide, pas d'exception
    }

    @Test
    void classeLesUrlParRequetesPuisParNomEtLimiteAuTopN() {
        StatsStore s = new StatsStore(5, 60);
        s.apply(ev("A", 95_000, "/b", 5, 0));
        s.apply(ev("A", 95_000, "/a", 5, 0));
        s.apply(ev("A", 95_000, "/checkout", 9, 4));
        s.apply(ev("A", 95_000, "/zzz", 1, 0));

        List<StatsStore.UrlStats> top = s.summary("A", NOW, 1, 3).topUrls();
        assertEquals(3, top.size());
        assertEquals("/checkout", top.get(0).url());
        assertEquals("/a", top.get(1).url());
        assertEquals("/b", top.get(2).url());
    }

    @Test
    void additionneUneMemeUrlSurPlusieursFenetres() {
        StatsStore s = new StatsStore(5, 60);
        s.apply(ev("A", 90_000, "/checkout", 3, 1));
        s.apply(ev("A", 95_000, "/checkout", 4, 2));

        StatsStore.UrlStats u = s.summary("A", NOW, 2, 8).topUrls().get(0);
        assertEquals(7, u.requests());
        assertEquals(3, u.errors5xx());
    }

    @Test
    void tauxDErreurs() {
        StatsStore s = new StatsStore(5, 60);
        assertEquals(0.0, s.summary("A", NOW, 1, 8).errorRate());   // aucune requête : 0, pas de division par zéro

        s.apply(ev("A", 95_000, "/", 8, 2));
        assertEquals(0.25, s.summary("A", NOW, 1, 8).errorRate(), 1e-9);
    }

    @Test
    void oublieLesFenetresTropAnciennes() {
        StatsStore s = new StatsStore(5, 2);            // on ne garde que 2 fenêtres (10 s)
        s.apply(ev("A", 50_000, "/vieux", 99, 0));
        s.apply(ev("A", 95_000, "/recent", 1, 0));      // la fenêtre 50_000 est maintenant hors mémoire

        long nowAtOldWindow = 52_000L;                  // la fenêtre 50_000 serait "terminée" à ce moment-là
        assertTrue(s.summary("A", nowAtOldWindow + 5_000, 1, 8).topUrls().isEmpty());
    }
}
