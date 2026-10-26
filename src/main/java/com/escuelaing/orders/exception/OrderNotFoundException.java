package com.escuelaing.orders.exception;

/** Thrown when an operation refers to an order id that does not exist. */
public class OrderNotFoundException extends OrderException {

    public static final String ERROR_CODE = "ORDER_NOT_FOUND";

    public OrderNotFoundException(String orderId) {
        super(ERROR_CODE, "Order " + orderId + " does not exist");
    }
}
