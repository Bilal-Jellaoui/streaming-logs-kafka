package com.soukline.controller;

import com.soukline.service.PanneService;
import com.soukline.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final PanneService panneService;
    private final ProductService productService;

    public AdminController(PanneService panneService, ProductService productService) {
        this.panneService = panneService;
        this.productService = productService;
    }

    /**
     * Dashboard d'administration (/admin)
     */
    @GetMapping
    public String adminDashboard(Model model) {
        model.addAttribute("panneActive", panneService.isPanneActive());
        model.addAttribute("triggerCount", panneService.getPanneTriggerCount());
        model.addAttribute("lastChange", panneService.getLastStateChange());
        model.addAttribute("productsCount", productService.getAllProducts().size());
        return "admin";
    }

    /**
     * Endpoint Requis par le Sujet : /admin/panne?etat=on|off
     * Fonctionne en GET et POST pour s'intégrer facilement avec JMeter, curl et navigateurs.
     */
    @RequestMapping(value = "/panne", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handlePanne(
            @RequestParam(value = "etat", required = false) String etat,
            @RequestParam(value = "format", required = false) String format,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (etat != null) {
            String cleanEtat = etat.trim().toLowerCase();
            if ("on".equals(cleanEtat) || "true".equals(cleanEtat) || "1".equals(cleanEtat)) {
                panneService.setPanneActive(true);
            } else if ("off".equals(cleanEtat) || "false".equals(cleanEtat) || "0".equals(cleanEtat)) {
                panneService.setPanneActive(false);
            } else if ("toggle".equals(cleanEtat)) {
                panneService.toggle();
            }
        }

        boolean currentPanne = panneService.isPanneActive();
        response.put("status", "SUCCESS");
        response.put("panneActive", currentPanne);
        response.put("etat", currentPanne ? "on" : "off");
        response.put("httpCheckoutExpectedStatus", currentPanne ? 503 : 200);
        response.put("triggerCount", panneService.getPanneTriggerCount());
        response.put("message", currentPanne
                ? "Mode panne ACTIVÉ: /checkout renverra désormais HTTP 503 Service Unavailable"
                : "Mode panne DÉSACTIVÉ: /checkout fonctionne normalement (HTTP 200)");

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint API pour le toggle AJAX dans l'interface UI
     */
    @PostMapping("/panne/toggle")
    @ResponseBody
    public Map<String, Object> togglePanne() {
        boolean newState = panneService.toggle();
        Map<String, Object> response = new HashMap<>();
        response.put("panneActive", newState);
        response.put("etat", newState ? "on" : "off");
        response.put("triggerCount", panneService.getPanneTriggerCount());
        return response;
    }
}
