package com.escuelaing.orders.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escuelaing.orders.model.FindOrdersByCustomerRequest;
import com.escuelaing.orders.model.Order;
import java.util.List;
import org.junit.jupiter.api.Test;

class FindOrdersByCustomerHandlerTest {

    private final FindOrdersByCustomerHandler handler = new FindOrdersByCustomerHandler();

    @Test
    void findsThePreloadedOrdersOfACustomer() {
        List<Order> found = handler.handleRequest(new FindOrdersByCustomerRequest("CUST-42"), null);

        assertEquals(List.of("ORD-001", "ORD-002"), found.stream().map(Order::getId).toList());
    }

    @Test
    void returnsAnEmptyListForACustomerWithoutOrders() {
        // 200 OK with [], not 404: the collection exists, it simply has no match.
        assertTrue(handler.handleRequest(new FindOrdersByCustomerRequest("CUST-100"), null).isEmpty());
    }

    @Test
    void reportsAMissingCustomerIdWithTheCodeApiGatewayMapsTo400() {
        FunctionErrorException error = assertThrows(FunctionErrorException.class,
                () -> handler.handleRequest(new FindOrdersByCustomerRequest(""), null));

        assertEquals("{\"error\":\"INVALID_ORDER\","
                + "\"message\":\"Query parameter 'customerId' is required\"}", error.getMessage());
    }

    @Test
    void reportsAnAbsentPayloadAsInvalidInput() {
        assertThrows(FunctionErrorException.class, () -> handler.handleRequest(null, null));
    }
}
