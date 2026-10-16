package com.soukline.service;

import com.soukline.model.Cart;
import com.soukline.model.Product;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service gérant le panier client associé à la session HTTP en mémoire.
 */
@Service
public class CartService {

    public static final String SESSION_CART_KEY = "SOUKLINE_CART";

    private final ProductService productService;

    public CartService(ProductService productService) {
        this.productService = productService;
    }

    public Cart getCart(HttpSession session) {
        Cart cart = (Cart) session.getAttribute(SESSION_CART_KEY);
        if (cart == null) {
            cart = new Cart();
            session.setAttribute(SESSION_CART_KEY, cart);
        }
        return cart;
    }

    public boolean addToCart(HttpSession session, Long productId, int quantity) {
        Optional<Product> productOpt = productService.getProductById(productId);
        if (productOpt.isPresent()) {
            Cart cart = getCart(session);
            cart.addItem(productOpt.get(), quantity > 0 ? quantity : 1);
            return true;
        }
        return false;
    }

    public void updateQuantity(HttpSession session, Long productId, int quantity) {
        Cart cart = getCart(session);
        cart.updateQuantity(productId, quantity);
    }

    public void removeFromCart(HttpSession session, Long productId) {
        Cart cart = getCart(session);
        cart.removeItem(productId);
    }

    public void clearCart(HttpSession session) {
        Cart cart = getCart(session);
        cart.clear();
    }
}
