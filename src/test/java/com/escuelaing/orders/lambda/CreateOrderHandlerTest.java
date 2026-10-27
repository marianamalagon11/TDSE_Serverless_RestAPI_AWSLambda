package com.escuelaing.orders.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escuelaing.orders.exception.InvalidOrderException;
import com.escuelaing.orders.model.CreateOrderRequest;
import com.escuelaing.orders.model.Order;
import org.junit.jupiter.api.Test;

/**
 * The handler can be exercised as an ordinary Java object. The {@code Context} is passed as
 * {@code null} because the handler does not use it, which is itself the point: there is nothing
 * about this class that requires a running Lambda.
 */
class CreateOrderHandlerTest {

    private final CreateOrderHandler handler = new CreateOrderHandler();

    @Test
    void createsAnOrderFromTheInvocationPayload() {
        Order created = handler.handleRequest(
                new CreateOrderRequest("CUST-42", "MacBook Air", 1), null);

        assertNotNull(created.getId());
        assertEquals("CUST-42", created.getCustomerId());
        assertEquals("MacBook Air", created.getProduct());
        assertEquals(1, created.getQuantity());
    }

    @Test
    void translatesAnApplicationFailureIntoTheContractedErrorDocument() {
        FunctionErrorException error = assertThrows(FunctionErrorException.class,
                () -> handler.handleRequest(new CreateOrderRequest("CUST-42", "MacBook Air", 0), null));

        assertEquals("{\"error\":\"INVALID_ORDER\","
                + "\"message\":\"Field 'quantity' must be greater than zero\"}", error.getMessage());
        assertEquals(InvalidOrderException.class, error.getCause().getClass());
    }

    @Test
    void reportsAMissingPayloadAsInvalidInput() {
        FunctionErrorException error =
                assertThrows(FunctionErrorException.class, () -> handler.handleRequest(null, null));

        assertEquals("{\"error\":\"INVALID_ORDER\","
                + "\"message\":\"A request body is required\"}", error.getMessage());
    }
}
