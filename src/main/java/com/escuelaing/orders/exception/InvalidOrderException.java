package com.escuelaing.orders.exception;

/** Thrown when the input of an operation does not satisfy the rules of the application. */
public class InvalidOrderException extends OrderException {

    public static final String ERROR_CODE = "INVALID_ORDER";

    public InvalidOrderException(String message) {
        super(ERROR_CODE, message);
    }
}
