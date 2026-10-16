package com.soukline.controller;

import com.soukline.model.Cart;
import com.soukline.model.User;
import com.soukline.service.CartService;
import com.soukline.service.PanneService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Fournit automatiquement les attributs globaux (panier, statut panne, utilisateur) à tous les templates.
 */
@ControllerAdvice
public class GlobalModelAttributesAdvice {

    private final CartService cartService;
    private final PanneService panneService;

    public GlobalModelAttributesAdvice(CartService cartService, PanneService panneService) {
        this.cartService = cartService;
        this.panneService = panneService;
    }

    @ModelAttribute("cartItemCount")
    public int getCartItemCount(HttpSession session) {
        Cart cart = cartService.getCart(session);
        return cart != null ? cart.getTotalItemsCount() : 0;
    }

    @ModelAttribute("isPanneActive")
    public boolean isPanneActive() {
        return panneService.isPanneActive();
    }

    @ModelAttribute("currentUser")
    public User getCurrentUser(HttpSession session) {
        return (User) session.getAttribute("SOUKLINE_USER");
    }
}
