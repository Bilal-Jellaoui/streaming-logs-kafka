package com.soukline.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

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
            return body;
        }

        model.addAttribute("status", 503);
        model.addAttribute("error", "Service de paiement indisponible (Mode Panne)");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        return "error/503";
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handle404(NoHandlerFoundException ex, HttpServletRequest request, Model model) {
        logger.warn("⚠️ Route introuvable 404: {}", request.getRequestURI());
        model.addAttribute("status", 404);
        model.addAttribute("error", "Page non trouvée");
        model.addAttribute("message", "La ressource demandée n'existe pas sur le serveur.");
        model.addAttribute("path", request.getRequestURI());
        return "error/404";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneralException(Exception ex, HttpServletRequest request, Model model) {
        logger.error("❌ Exception inattendue sur {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        model.addAttribute("status", 500);
        model.addAttribute("error", "Erreur Interne du Serveur");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        return "error/500";
    }
}
