package com.escuelaing.orders.lambda;

import com.escuelaing.orders.exception.OrderException;

/**
 * Carries a failure out of a Lambda function in the shape API Gateway is able to route on.
 *
 * <p>In a custom (non-proxy) integration, API Gateway decides the HTTP status code by matching a
 * regular expression against the {@code errorMessage} field of the Lambda error, and against
 * nothing else: the exception type and any structured field are invisible to it. The error code of
 * the application therefore has to travel inside the message text.
 *
 * <p>The message is built as the error document of the API contract, so the integration response
 * can hand it to the client unchanged with {@code $input.path('$.errorMessage')} instead of
 * exposing {@code errorType} and the stack trace.
 *
 * <p>This class is the price of the architecture, and it is deliberately confined to the AWS layer
 * so that {@code OrderService} keeps reporting plain business messages.
 */
public class FunctionErrorException extends RuntimeException {

    static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private FunctionErrorException(String errorDocument, Throwable cause) {
        super(errorDocument, cause);
    }

    /** A failure the application reported, with its own error code. */
    public static FunctionErrorException from(OrderException cause) {
        return new FunctionErrorException(toJson(cause.getErrorCode(), cause.getMessage()), cause);
    }

    /**
     * Anything unexpected. The real cause stays attached, so the runtime writes it to CloudWatch,
     * while the client only receives a generic message: internal details are not part of the API.
     */
    public static FunctionErrorException internal(Throwable cause) {
        return new FunctionErrorException(
                toJson(INTERNAL_ERROR, "The request could not be processed"), cause);
    }

    static String toJson(String error, String message) {
        return "{\"error\":\"" + escape(error) + "\",\"message\":\"" + escape(message) + "\"}";
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
