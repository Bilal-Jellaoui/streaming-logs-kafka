package com.soukline.model;

public class CartItem {
    private Product product;
    private int quantity;

    public CartItem() {
    }

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getTotalPrice() {
        if (product == null) return 0.0;
        return product.getPrice() * quantity;
    }

    public String getFormattedTotalPrice() {
        return String.format("%.2f DH", getTotalPrice());
    }
}
