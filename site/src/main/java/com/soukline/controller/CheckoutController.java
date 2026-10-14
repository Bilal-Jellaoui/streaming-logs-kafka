package com.soukline.controller;

import com.soukline.model.Cart;
import com.soukline.model.Order;
import com.soukline.model.User;
import com.soukline.service.CartService;
import com.soukline.service.PanneService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.UUID;

@Controller
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);

    private final CartService cartService;
    private final PanneService panneService;

    public CheckoutController(CartService cartService, PanneService panneService) {
        this.cartService = cartService;
        this.panneService = panneService;
    }

    /**
     * Route GET /checkout
     * Si le mode panne est actif, renvoie IMMÉDIATEMENT un code HTTP 503.
     */
    @GetMapping("/checkout")
    public String checkoutPage(HttpSession session, Model model, HttpServletRequest request, HttpServletResponse response) {
        if (panneService.isPanneActive()) {
            panneService.incrementPanneTriggerCount();
            logger.error("🚨 [PANNE 503] Requête GET /checkout bloquée - Passerelle de paiement en panne ! IP: {}", request.getRemoteAddr());
            throw new PanneSimuleeException("Passerelle de paiement SoukLine indisponible (Mode Panne activé)");
        }

        Cart cart = cartService.getCart(session);
        if (cart.isEmpty()) {
            return "redirect:/panier";
        }

        model.addAttribute("cart", cart);
        User user = (User) session.getAttribute(AuthController.SESSION_USER_KEY);
        model.addAttribute("user", user);

        return "checkout";
    }

    /**
     * Route POST /checkout
     * Si le mode panne est actif, le paiement échoue avec une erreur HTTP 503.
     * Sinon, finalise la commande et redirige vers /confirmation.
     */
    @PostMapping("/checkout")
    public String processCheckout(
            @RequestParam(value = "nom", defaultValue = "Client Invité") String nom,
            @RequestParam(value = "email", defaultValue = "client@soukline.ma") String email,
            @RequestParam(value = "adresse", defaultValue = "123 Avenue Mohammed V") String adresse,
            @RequestParam(value = "ville", defaultValue = "Casablanca") String ville,
            @RequestParam(value = "carte", required = false) String carte,
            HttpSession session,
            HttpServletRequest request) {

        if (panneService.isPanneActive()) {
            panneService.incrementPanneTriggerCount();
            logger.error("🚨 [PANNE 503] Requête POST /checkout - Transaction de paiement rejetée pour {} (Mode Panne activé)", email);
            throw new PanneSimuleeException("Échec de la transaction : Le processeur bancaire a renvoyé un statut 503 (Mode Panne actif)");
        }

        Cart cart = cartService.getCart(session);
        if (cart.isEmpty()) {
            return "redirect:/panier";
        }

        // Création de la commande réussie
        String orderId = "SL-" + (10000 + (int)(Math.random() * 90000));
        Order order = new Order(
                orderId,
                LocalDateTime.now(),
                cart.getItems(),
                cart.getSubtotal(),
                cart.getShippingFee(),
                cart.getTotal(),
                nom,
                email,
                adresse,
                ville,
                "Carte Bancaire CMI (Sécurisé)",
                "CONFIRMEE"
        );

        session.setAttribute("LAST_ORDER", order);
        logger.info("🎉 [COMMANDE VALIDEE] Commande {} créée avec succès pour un montant de {} (Client: {})",
                orderId, order.getFormattedTotal(), email);

        // Vider le panier après paiement réussi
        cartService.clearCart(session);

        return "redirect:/confirmation";
    }

    /**
     * Route GET /confirmation
     */
    @GetMapping("/confirmation")
    public String confirmationPage(HttpSession session, Model model) {
        Order order = (Order) session.getAttribute("LAST_ORDER");
        if (order == null) {
            // Création d'une commande d'exemple si consultation directe
            order = new Order(
                    "SL-84920",
                    LocalDateTime.now(),
                    java.util.Collections.emptyList(),
                    470.00,
                    0.00,
                    470.00,
                    "Client SoukLine",
                    "client@soukline.ma",
                    "Boulevard d'Anfa",
                    "Casablanca",
                    "Carte Bancaire",
                    "CONFIRMEE"
            );
        }

        model.addAttribute("order", order);
        return "confirmation";
    }
}
