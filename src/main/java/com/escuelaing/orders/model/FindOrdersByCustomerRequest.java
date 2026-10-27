package com.escuelaing.orders.model;

/**
 * Input of the "find orders belonging to a customer" operation.
 *
 * <p>API Gateway reads the query parameter of {@code GET /orders?customerId=CUST-42} and builds
 * {@code {"customerId": "CUST-42"}} out of it.
 */
public class FindOrdersByCustomerRequest {

    private String customerId;

    public FindOrdersByCustomerRequest() {
        // required by the JSON deserializer
    }

    public FindOrdersByCustomerRequest(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @Override
    public String toString() {
        return "FindOrdersByCustomerRequest{customerId='" + customerId + "'}";
    }
}
