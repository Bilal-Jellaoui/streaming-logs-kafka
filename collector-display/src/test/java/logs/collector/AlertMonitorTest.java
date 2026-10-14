package logs.collector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertMonitorTest {

    private static StatsStore.Summary summary(String engine, long requests, long errors) {
        return new StatsStore.Summary(engine, requests, errors, List.of());
    }

    @Test
    void pasDAlerteSousLeSeuil() {
        AlertMonitor m = new AlertMonitor(10.0, 10);

        AlertMonitor.State s = m.evaluate(summary("A", 100, 5), summary("B", 100, 5));
        assertFalse(s.alerting());
        assertEquals(5.0, s.ratePercent(), 1e-9);
    }

    @Test
    void alerteAuDessusDuSeuil() {
        AlertMonitor m = new AlertMonitor(10.0, 10);

        AlertMonitor.State s = m.evaluate(summary("A", 100, 40), summary("B", 100, 40));
        assertTrue(s.alerting());
        assertEquals(40.0, s.ratePercent(), 1e-9);
        assertEquals("A", s.source());
    }

    @Test
    void egaliteAuSeuilNeDeclenchePas() {
        AlertMonitor m = new AlertMonitor(10.0, 10);

        assertFalse(m.evaluate(summary("A", 100, 10), summary("B", 0, 0)).alerting());
    }

    @Test
    void pasDAlertePourTresPeuDeRequetes() {
        AlertMonitor m = new AlertMonitor(10.0, 10);

        // 1 erreur sur 2 requêtes = 50 %, mais 2 requêtes seulement : pas assez pour juger
        assertFalse(m.evaluate(summary("A", 2, 1), summary("B", 0, 0)).alerting());
    }

    @Test
    void justRaisedSeulementAuPassageEnAlerte() {
        AlertMonitor m = new AlertMonitor(10.0, 10);

        assertFalse(m.evaluate(summary("A", 100, 1), summary("B", 0, 0)).justRaised());
        assertTrue(m.evaluate(summary("A", 100, 50), summary("B", 0, 0)).justRaised());     // OK -> ALERTE
        assertFalse(m.evaluate(summary("A", 100, 50), summary("B", 0, 0)).justRaised());    // toujours en alerte
        assertFalse(m.evaluate(summary("A", 100, 1), summary("B", 0, 0)).alerting());       // retour à OK
        assertTrue(m.evaluate(summary("A", 100, 50), summary("B", 0, 0)).justRaised());     // nouvelle alerte
    }

    @Test
    void sappuieSurLeMoteurBSiLeMoteurAEstSilencieux() {
        AlertMonitor m = new AlertMonitor(10.0, 10);

        AlertMonitor.State s = m.evaluate(summary("A", 0, 0), summary("B", 50, 30));
        assertTrue(s.alerting());
        assertEquals("B", s.source());
    }
}
