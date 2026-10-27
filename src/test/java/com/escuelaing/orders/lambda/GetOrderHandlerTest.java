package com.escuelaing.orders.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void reportsAMissingOrderWithTheCodeApiGatewayMapsTo404() {
        FunctionErrorException error = assertThrows(FunctionErrorException.class,
                () -> handler.handleRequest(new GetOrderRequest("ORD-999"), null));

        assertEquals("{\"error\":\"ORDER_NOT_FOUND\","
                + "\"message\":\"Order ORD-999 does not exist\"}", error.getMessage());
    }

    @Test
    void reportsAnEmptyIdWithTheCodeApiGatewayMapsTo400() {
        // A misconfigured mapping template would produce {"id": ""} or an empty document.
        for (GetOrderRequest broken :
                new GetOrderRequest[] {new GetOrderRequest(""), new GetOrderRequest(null), null}) {
            FunctionErrorException error = assertThrows(FunctionErrorException.class,
                    () -> handler.handleRequest(broken, null));

            assertEquals("{\"error\":\"INVALID_ORDER\","
                    + "\"message\":\"Field 'id' is required\"}", error.getMessage());
        }
    }
}
