package com.escuelaing.orders.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escuelaing.orders.exception.InvalidOrderException;
import com.escuelaing.orders.exception.OrderNotFoundException;
import com.escuelaing.orders.model.GetOrderRequest;
import com.escuelaing.orders.model.Order;
import org.junit.jupiter.api.Test;

class GetOrderHandlerTest {

    private final GetOrderHandler handler = new GetOrderHandler();

    @Test
    void retrievesOneOfThePreloadedOrders() {
        Order found = handler.handleRequest(new GetOrderRequest("ORD-001"), null);

        assertEquals("ORD-001", found.getId());
        assertEquals("CUST-42", found.getCustomerId());
    }

    @Test
    void failsWhenTheOrderDoesNotExist() {
        OrderNotFoundException error = assertThrows(OrderNotFoundException.class,
                () -> handler.handleRequest(new GetOrderRequest("ORD-999"), null));

        assertEquals("Order ORD-999 does not exist", error.getMessage());
    }

    @Test
    void failsWhenApiGatewaySendsNoIdAtAll() {
        // A misconfigured mapping template would produce {"id": ""} or an empty document.
        assertThrows(InvalidOrderException.class,
                () -> handler.handleRequest(new GetOrderRequest(""), null));
        assertThrows(InvalidOrderException.class,
                () -> handler.handleRequest(new GetOrderRequest(null), null));
        assertThrows(InvalidOrderException.class, () -> handler.handleRequest(null, null));
    }
}
