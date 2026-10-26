package com.escuelaing.orders.exception;

/**
 * Base class for the errors the Orders application can report.
 *
 * <p>Each subclass carries a stable, machine-readable error code, the same code the API contract
 * publishes in the {@code error} field of an error body. The application says *what* went wrong;
 * deciding which HTTP status code that maps to is not its job.
 */
public abstract class OrderException extends RuntimeException {

    private final String errorCode;

    protected OrderException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
