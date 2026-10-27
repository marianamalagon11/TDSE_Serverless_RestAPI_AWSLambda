package com.escuelaing.orders.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escuelaing.orders.model.DeleteOrderRequest;
import org.junit.jupiter.api.Test;

class DeleteOrderHandlerTest {

    private final DeleteOrderHandler handler = new DeleteOrderHandler();

    @Test
    void deletesOneOfThePreloadedOrdersAndReturnsNothing() {
        assertNull(handler.handleRequest(new DeleteOrderRequest("ORD-002"), null));
    }

    @Test
    void reportsAMissingOrderWithTheCodeApiGatewayMapsTo404() {
        FunctionErrorException error = assertThrows(FunctionErrorException.class,
                () -> handler.handleRequest(new DeleteOrderRequest("ORD-999"), null));

        assertEquals("{\"error\":\"ORDER_NOT_FOUND\","
                + "\"message\":\"Order ORD-999 does not exist\"}", error.getMessage());
    }

    @Test
    void reportsAnEmptyIdWithTheCodeApiGatewayMapsTo400() {
        FunctionErrorException error = assertThrows(FunctionErrorException.class,
                () -> handler.handleRequest(new DeleteOrderRequest(""), null));

        assertEquals("{\"error\":\"INVALID_ORDER\","
                + "\"message\":\"Field 'id' is required\"}", error.getMessage());
    }
}
