package com.soukline.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service de gestion du Mode Panne pour la démonstration de streaming et alerting.
 * Contrôlé via /admin/panne?etat=on|off
 */
@Service
public class PanneService {
    private static final Logger logger = LoggerFactory.getLogger(PanneService.class);

    private final AtomicBoolean panneActive = new AtomicBoolean(false);
    private final AtomicInteger panneTriggerCount = new AtomicInteger(0);
    private volatile LocalDateTime lastStateChange = LocalDateTime.now();

    public boolean isPanneActive() {
        return panneActive.get();
    }

    public synchronized void setPanneActive(boolean active) {
        boolean previous = panneActive.getAndSet(active);
        lastStateChange = LocalDateTime.now();
        if (previous != active) {
            if (active) {
                logger.warn("⚠️ [MODE PANNE ACTIVE] Les requêtes sur /checkout renverront désormais des erreurs HTTP 503 !");
            } else {
                logger.info("✅ [MODE PANNE DESACTIVE] Le service de paiement /checkout est revenu à la normale (HTTP 200).");
            }
        }
    }

    public synchronized boolean toggle() {
        boolean newState = !panneActive.get();
        setPanneActive(newState);
        return newState;
    }

    public int incrementPanneTriggerCount() {
        return panneTriggerCount.incrementAndGet();
    }

    public int getPanneTriggerCount() {
        return panneTriggerCount.get();
    }

    public LocalDateTime getLastStateChange() {
        return lastStateChange;
    }
}
