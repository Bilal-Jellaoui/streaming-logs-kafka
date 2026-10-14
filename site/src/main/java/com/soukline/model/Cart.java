package com.soukline.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Cart implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<Long, CartItem> items = new LinkedHashMap<>();

    public synchronized void addItem(Product product, int quantity) {
        if (product == null || quantity <= 0) return;
        Long id = product.getId();
        if (items.containsKey(id)) {
            CartItem existing = items.get(id);
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            items.put(id, new CartItem(product, quantity));
        }
    }

    public synchronized void updateQuantity(Long productId, int quantity) {
        if (quantity <= 0) {
            removeItem(productId);
        } else if (items.containsKey(productId)) {
            items.get(productId).setQuantity(quantity);
        }
    }

    public synchronized void removeItem(Long productId) {
        items.remove(productId);
    }

    public synchronized void clear() {
        items.clear();
    }

    public synchronized List<CartItem> getItems() {
        return new ArrayList<>(items.values());
    }

    public synchronized int getTotalItemsCount() {
        int total = 0;
        for (CartItem item : items.values()) {
            total += item.getQuantity();
        }
        return total;
    }

    public synchronized double getSubtotal() {
        double subtotal = 0.0;
        for (CartItem item : items.values()) {
            subtotal += item.getTotalPrice();
        }
        return subtotal;
    }

    public synchronized double getShippingFee() {
        if (items.isEmpty()) return 0.0;
        // Livraison offerte dès 300 DH d'achat
        return getSubtotal() >= 300.0 ? 0.0 : 25.0;
    }

    public synchronized double getTotal() {
        if (items.isEmpty()) return 0.0;
        return getSubtotal() + getShippingFee();
    }

    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }

    public String getFormattedSubtotal() {
        return String.format("%.2f DH", getSubtotal());
    }

    public String getFormattedShippingFee() {
        double fee = getShippingFee();
        return fee == 0.0 ? "GRATUIT" : String.format("%.2f DH", fee);
    }

    public String getFormattedTotal() {
        return String.format("%.2f DH", getTotal());
    }
}
