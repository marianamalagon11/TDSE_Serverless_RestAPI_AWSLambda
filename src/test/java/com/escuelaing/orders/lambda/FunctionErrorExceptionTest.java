package com.escuelaing.orders.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escuelaing.orders.exception.InvalidOrderException;
import com.escuelaing.orders.exception.OrderNotFoundException;
import org.junit.jupiter.api.Test;

/**
 * The message of this exception is what API Gateway matches its regular expressions against and
 * what the client finally receives, so its exact text is part of the API contract and is pinned
 * down here.
 */
class FunctionErrorExceptionTest {

    @Test
    void buildsTheContractedErrorDocumentForAMissingOrder() {
        FunctionErrorException error =
                FunctionErrorException.from(new OrderNotFoundException("ORD-999"));

        assertEquals("{\"error\":\"ORDER_NOT_FOUND\","
                + "\"message\":\"Order ORD-999 does not exist\"}", error.getMessage());
    }

    @Test
    void buildsTheContractedErrorDocumentForInvalidInput() {
        FunctionErrorException error =
                FunctionErrorException.from(new InvalidOrderException("Field 'quantity' is required"));

        assertEquals("{\"error\":\"INVALID_ORDER\","
                + "\"message\":\"Field 'quantity' is required\"}", error.getMessage());
    }

    @Test
    void hidesTheDetailsOfAnUnexpectedFailureFromTheClient() {
        IllegalStateException cause = new IllegalStateException("connection pool exhausted at line 42");

        FunctionErrorException error = FunctionErrorException.internal(cause);

        assertEquals("{\"error\":\"INTERNAL_ERROR\","
                + "\"message\":\"The request could not be processed\"}", error.getMessage());
        assertTrue(error.getMessage().indexOf("connection pool") < 0);
        // The real cause still travels with the exception, so the runtime logs it to CloudWatch.
        assertSame(cause, error.getCause());
    }

    @Test
    void keepsTheDocumentValidWhenTheMessageContainsQuotesOrBackslashes() {
        String json = FunctionErrorException.toJson("INVALID_ORDER", "a \"quoted\" path C:\\tmp\nnext");

        assertEquals("{\"error\":\"INVALID_ORDER\","
                + "\"message\":\"a \\\"quoted\\\" path C:\\\\tmp\\nnext\"}", json);
    }

    @Test
    void keepsTheErrorCodeFindableByARegularExpression() {
        // This is what selectionPattern ".*ORDER_NOT_FOUND.*" relies on in API Gateway.
        String message = FunctionErrorException.from(new OrderNotFoundException("ORD-1")).getMessage();

        assertTrue(message.matches(".*ORDER_NOT_FOUND.*"));
    }
}
