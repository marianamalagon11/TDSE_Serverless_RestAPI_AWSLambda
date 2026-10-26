package com.escuelaing.orders.model;

/**
 * Input of the "create an order" operation.
 *
 * <p>Deliberately a mutable JavaBean: the AWS Lambda Java runtime deserializes the incoming JSON
 * into a POJO using Jackson, which needs a public no-argument constructor plus setters. The
 * all-arguments constructor exists for the tests and for callers that build the request by hand.
 *
 * <p>Note that it carries no {@code id}: the id is assigned by the application, not by the client.
 */
public class CreateOrderRequest {

    private String customerId;
    private String product;
    private Integer quantity;

    public CreateOrderRequest() {
        // required by the JSON deserializer
    }

    public CreateOrderRequest(String customerId, String product, Integer quantity) {
        this.customerId = customerId;
        this.product = product;
        this.quantity = quantity;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "CreateOrderRequest{customerId='" + customerId
                + "', product='" + product + "', quantity=" + quantity + "}";
    }
}
