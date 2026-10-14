package com.soukline.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderId;
    private LocalDateTime orderDate;
    private List<CartItem> items = new ArrayList<>();
    private double subtotal;
    private double shippingFee;
    private double total;
    private String customerName;
    private String customerEmail;
    private String address;
    private String city;
    private String paymentMethod;
    private String status;

    public Order() {
    }

    public Order(String orderId, LocalDateTime orderDate, List<CartItem> items,
                 double subtotal, double shippingFee, double total,
                 String customerName, String customerEmail, String address,
                 String city, String paymentMethod, String status) {
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.items = items;
        this.subtotal = subtotal;
        this.shippingFee = shippingFee;
        this.total = total;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.address = address;
        this.city = city;
        this.paymentMethod = paymentMethod;
        this.status = status;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public String getFormattedDate() {
        if (orderDate == null) return "";
        return orderDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }

    public double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(double subtotal) {
        this.subtotal = subtotal;
    }

    public double getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(double shippingFee) {
        this.shippingFee = shippingFee;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public String getFormattedTotal() {
        return String.format("%.2f DH", total);
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
