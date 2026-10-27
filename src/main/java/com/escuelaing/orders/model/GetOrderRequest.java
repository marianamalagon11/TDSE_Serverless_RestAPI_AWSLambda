package com.escuelaing.orders.model;

/**
 * Input of the "retrieve an order" operation.
 *
 * <p>It exists so that the Lambda receives a small, named document ({@code {"id": "ORD-42"}})
 * instead of a complete HTTP request. Building that document out of the URL is the job of API
 * Gateway, and this class is the Java side of that agreement.
 *
 * <p>Like {@link CreateOrderRequest} it is a JavaBean, because the runtime populates it through
 * its no-argument constructor and its setters.
 */
public class GetOrderRequest {

    private String id;

    public GetOrderRequest() {
        // required by the JSON deserializer
    }

    public GetOrderRequest(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "GetOrderRequest{id='" + id + "'}";
    }
}
