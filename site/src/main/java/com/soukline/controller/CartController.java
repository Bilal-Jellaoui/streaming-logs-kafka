package com.soukline.controller;

import com.soukline.model.Cart;
import com.soukline.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/panier")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public String viewCart(HttpSession session, Model model) {
        Cart cart = cartService.getCart(session);
        model.addAttribute("cart", cart);
        return "panier";
    }

    @PostMapping("/ajouter")
    public String addToCart(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "quantity", defaultValue = "1") int quantity,
            @RequestParam(value = "redirect", required = false) String redirect,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        boolean added = cartService.addToCart(session, productId, quantity);
        if (added) {
            redirectAttributes.addFlashAttribute("successMessage", "Article ajouté au panier avec succès !");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Impossible d'ajouter cet article.");
        }

        if (redirect != null && !redirect.isEmpty()) {
            return "redirect:" + redirect;
        }
        return "redirect:/panier";
    }

    // Endpoint API AJAX pour ajout rapide au panier depuis la liste
    @PostMapping("/api/ajouter")
    @ResponseBody
    public Map<String, Object> addToCartApi(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "quantity", defaultValue = "1") int quantity,
            HttpSession session) {

        boolean added = cartService.addToCart(session, productId, quantity);
        Cart cart = cartService.getCart(session);

        Map<String, Object> response = new HashMap<>();
        response.put("success", added);
        response.put("cartCount", cart.getTotalItemsCount());
        response.put("subtotal", cart.getFormattedSubtotal());
        return response;
    }

    @PostMapping("/modifier")
    public String updateQuantity(
            @RequestParam("productId") Long productId,
            @RequestParam("quantity") int quantity,
            HttpSession session) {

        cartService.updateQuantity(session, productId, quantity);
        return "redirect:/panier";
    }

    @PostMapping("/supprimer")
    public String removeItem(
            @RequestParam("productId") Long productId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        cartService.removeFromCart(session, productId);
        redirectAttributes.addFlashAttribute("infoMessage", "Article retiré du panier.");
        return "redirect:/panier";
    }

    @GetMapping("/supprimer/{id}")
    public String removeItemGet(
            @PathVariable("id") Long productId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        cartService.removeFromCart(session, productId);
        redirectAttributes.addFlashAttribute("infoMessage", "Article retiré du panier.");
        return "redirect:/panier";
    }

    @PostMapping("/vider")
    public String clearCart(HttpSession session, RedirectAttributes redirectAttributes) {
        cartService.clearCart(session);
        redirectAttributes.addFlashAttribute("infoMessage", "Le panier a été vidé.");
        return "redirect:/panier";
    }
}
