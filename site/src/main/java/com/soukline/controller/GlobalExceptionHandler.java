package com.soukline.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire global des exceptions.
 * Enregistre les exceptions dans logs/error.log et garantit le retour des codes HTTP appropriés (503, 404, 500).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PanneSimuleeException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Object handlePanneException(PanneSimuleeException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        logger.error("🚨 [ALERTE PANNE 503] Requête sur {} a échoué: {}", request.getRequestURI(), ex.getMessage());

        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", 503);
            body.put("error", "Service Unavailable");
            body.put("message", ex.getMessage());
            body.put("path", request.getRequestURI());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

        model.addAttribute("status", 503);
        model.addAttribute("error", "Service de paiement indisponible (Mode Panne)");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        return "error/503";
    }

    /**
     * Traite les erreurs 404 (Route inexistante ou Ressource statique introuvable comme /favicon.ico ou /nimporte-quoi).
     * En Spring Boot 3, NoResourceFoundException est lancée pour toute ressource non trouvée.
     * Cette méthode garantit un code HTTP 404 dans access.log et évite de fausser les métriques 5xx de streaming.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Object handle404(Exception ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        logger.warn("⚠️ Route/Ressource introuvable 404: {} ({})", request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", 404);
            body.put("error", "Not Found");
            body.put("message", "La ressource demandée n'existe pas sur le serveur.");
            body.put("path", request.getRequestURI());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }

        model.addAttribute("status", 404);
        model.addAttribute("error", "Page non trouvée");
        model.addAttribute("message", "La ressource demandée n'existe pas sur le serveur.");
        model.addAttribute("path", request.getRequestURI());
        return "error/404";
    }

    /**
     * Traite les exceptions ResponseStatusException (notamment HTTP 404 pour les produits introuvables).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Object handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        int statusCode = ex.getStatusCode().value();
        response.setStatus(statusCode);

        if (statusCode == HttpStatus.NOT_FOUND.value()) {
            logger.warn("⚠️ Ressource introuvable 404: {}", request.getRequestURI());
            String accept = request.getHeader("Accept");
            if (accept != null && accept.contains("application/json")) {
                Map<String, Object> body = new HashMap<>();
                body.put("status", 404);
                body.put("error", "Not Found");
                body.put("message", ex.getReason() != null ? ex.getReason() : "Ressource introuvable");
                body.put("path", request.getRequestURI());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }

            model.addAttribute("status", 404);
            model.addAttribute("error", "Page non trouvée");
            model.addAttribute("message", ex.getReason() != null ? ex.getReason() : "La ressource demandée n'existe pas sur le serveur.");
            model.addAttribute("path", request.getRequestURI());
            return "error/404";
        }

        logger.error("❌ Exception ResponseStatusException {} sur {}: {}", statusCode, request.getRequestURI(), ex.getMessage());
        model.addAttribute("status", statusCode);
        model.addAttribute("error", ex.getStatusCode().toString());
        model.addAttribute("message", ex.getReason());
        model.addAttribute("path", request.getRequestURI());
        return "error/500";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneralException(Exception ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        logger.error("❌ Exception inattendue sur {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        model.addAttribute("status", 500);
        model.addAttribute("error", "Erreur Interne du Serveur");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        return "error/500";
    }
}
