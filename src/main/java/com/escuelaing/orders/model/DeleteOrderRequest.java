package com.escuelaing.orders.model;

/**
 * Input of the "delete an order" operation.
 *
 * <p>Identical in shape to {@link GetOrderRequest}, and kept separate because in the custom
 * integration each function publishes its own input document: merging them would couple two
 * operations that happen to agree today.
 */
public class DeleteOrderRequest {

    private String id;

    public DeleteOrderRequest() {
        // required by the JSON deserializer
    }

    public DeleteOrderRequest(String id) {
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
        return "DeleteOrderRequest{id='" + id + "'}";
    }
}
