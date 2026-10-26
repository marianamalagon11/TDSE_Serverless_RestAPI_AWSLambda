package com.escuelaing.orders.model;

import java.util.Objects;

/**
 * An order of the Orders API.
 *
 * <p>Immutable: once created, an order is never modified. The class only exposes getters, which
 * is also what a JSON serializer needs to turn it into the response body.
 */
public class Order {

    private final String id;
    private final String customerId;
    private final String product;
    private final int quantity;

    public Order(String id, String customerId, String product, int quantity) {
        this.id = id;
        this.customerId = customerId;
        this.product = product;
        this.quantity = quantity;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Order)) {
            return false;
        }
        Order that = (Order) other;
        return quantity == that.quantity
                && Objects.equals(id, that.id)
                && Objects.equals(customerId, that.customerId)
                && Objects.equals(product, that.product);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, customerId, product, quantity);
    }

    @Override
    public String toString() {
        return "Order{id='" + id + "', customerId='" + customerId
                + "', product='" + product + "', quantity=" + quantity + "}";
    }
}
