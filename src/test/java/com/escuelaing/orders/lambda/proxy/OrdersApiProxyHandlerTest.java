package com.escuelaing.orders.lambda.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.escuelaing.orders.model.Order;
import com.escuelaing.orders.repository.InMemoryOrderRepository;
import com.escuelaing.orders.service.OrderService;
import com.escuelaing.orders.service.SequentialOrderIdGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The whole HTTP behavior of the API, tested on a laptop.
 *
 * <p>This is the point of the proxy architecture: routing, status codes, headers and the error
 * format are Java, so they can be exercised without deploying anything. Several tests feed the
 * handler the **real** events captured from API Gateway in {@code docs/proxy-events/}, so the
 * assertions are made against what AWS actually sends and not against a hand written guess.
 */
class OrdersApiProxyHandlerTest {

    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    // A fresh application per test: same preloaded orders, same first generated id, no leakage.
    private final OrdersApiProxyHandler handler =
            new OrdersApiProxyHandler(OrderService.withDefaults());

    private static APIGatewayProxyRequestEvent capturedEvent(String name) throws IOException {
        return JSON.readValue(Path.of("docs", "proxy-events", name).toFile(),
                APIGatewayProxyRequestEvent.class);
    }

    private static JsonNode body(APIGatewayProxyResponseEvent response) throws IOException {
        return JSON.readTree(response.getBody());
    }

    @Test
    void answers404ForACapturedRequestAskingForAnOrderThatDoesNotExist() throws IOException {
        // The captured event asks for ORD-42, which is not among the preloaded orders.
        APIGatewayProxyResponseEvent response =
                handler.handleRequest(capturedEvent("get-order-by-id.json"), null);

        assertEquals(404, response.getStatusCode());
        assertEquals("ORDER_NOT_FOUND", body(response).get("error").asText());
        assertEquals("Order ORD-42 does not exist", body(response).get("message").asText());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
    }

    @Test
    void answers200ForAnOrderThatExists() throws IOException {
        APIGatewayProxyRequestEvent request = capturedEvent("get-order-by-id.json");
        request.setPathParameters(Map.of("id", "ORD-001"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

        assertEquals(200, response.getStatusCode());
        assertEquals("ORD-001", body(response).get("id").asText());
        assertEquals("CUST-42", body(response).get("customerId").asText());
        assertEquals(1, body(response).get("quantity").asInt());
    }

    @Test
    void answers200WithTheOrdersOfACustomer() throws IOException {
        APIGatewayProxyResponseEvent response =
                handler.handleRequest(capturedEvent("get-orders-by-customer.json"), null);

        assertEquals(200, response.getStatusCode());
        assertTrue(body(response).isArray());
        assertEquals(2, body(response).size());
        assertEquals("ORD-001", body(response).get(0).get("id").asText());
    }

    @Test
    void answers200WithAnEmptyArrayForACustomerWithoutOrders() throws IOException {
        APIGatewayProxyRequestEvent request = capturedEvent("get-orders-by-customer.json");
        request.setQueryStringParameters(Map.of("customerId", "CUST-100"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

        assertEquals(200, response.getStatusCode());
        assertEquals("[]", response.getBody());
    }

    @Test
    void answers400WhenTheQueryParameterIsMissingEntirely() throws IOException {
        APIGatewayProxyRequestEvent request = capturedEvent("get-orders-by-customer.json");
        // API Gateway sends null, not an empty map, when there is no query string at all.
        request.setQueryStringParameters(null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

        assertEquals(400, response.getStatusCode());
        assertEquals("INVALID_ORDER", body(response).get("error").asText());
    }

    @Test
    void answers201WithALocationHeaderWhenAnOrderIsCreated() throws IOException {
        APIGatewayProxyResponseEvent response =
                handler.handleRequest(capturedEvent("post-order.json"), null);

        assertEquals(201, response.getStatusCode());
        assertEquals("ORD-100", body(response).get("id").asText());
        assertEquals("CUST-42", body(response).get("customerId").asText());
        // Built from the path of the request itself, so it carries the stage.
        assertEquals("/dev/orders/ORD-100", response.getHeaders().get("Location"));
    }

    @Test
    void answers400WhenTheBodyIsNotValidJson() throws IOException {
        APIGatewayProxyRequestEvent request = capturedEvent("post-order.json");
        request.setBody("{not json at all");

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

        assertEquals(400, response.getStatusCode());
        assertEquals("MALFORMED_JSON", body(response).get("error").asText());
    }

    @Test
    void answers400WhenTheBodyIsAbsent() throws IOException {
        APIGatewayProxyRequestEvent request = capturedEvent("post-order.json");
        request.setBody(null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

        assertEquals(400, response.getStatusCode());
        assertEquals("INVALID_ORDER", body(response).get("error").asText());
    }

    @Test
    void answers400WhenTheOrderIsNotValid() throws IOException {
        APIGatewayProxyRequestEvent request = capturedEvent("post-order.json");
        request.setBody("{\"customerId\":\"CUST-42\",\"product\":\"Mouse\",\"quantity\":0}");

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

        assertEquals(400, response.getStatusCode());
        assertEquals("Field 'quantity' must be greater than zero",
                body(response).get("message").asText());
    }

    @Test
    void answers415WhenTheBodyIsNotDeclaredAsJson() throws IOException {
        APIGatewayProxyRequestEvent request = capturedEvent("post-order.json");
        request.setHeaders(Map.of("content-type", "application/x-www-form-urlencoded"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

        assertEquals(415, response.getStatusCode());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", body(response).get("error").asText());
    }

    @Test
    void acceptsARequestThatDeclaresNoContentTypeAtAll() throws IOException {
        APIGatewayProxyRequestEvent request = capturedEvent("post-order.json");
        request.setHeaders(null);

        assertEquals(201, handler.handleRequest(request, null).getStatusCode());
    }

    @Test
    void answers204WhenAnOrderIsDeleted() throws IOException {
        APIGatewayProxyRequestEvent request = capturedEvent("delete-order.json");
        request.setPathParameters(Map.of("id", "ORD-001"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

        assertEquals(204, response.getStatusCode());
        assertNull(response.getBody());
        assertTrue(response.getHeaders().isEmpty());
    }

    @Test
    void theDeletedOrderIsGoneAfterwards() throws IOException {
        APIGatewayProxyRequestEvent delete = capturedEvent("delete-order.json");
        delete.setPathParameters(Map.of("id", "ORD-001"));
        handler.handleRequest(delete, null);

        APIGatewayProxyRequestEvent get = capturedEvent("get-order-by-id.json");
        get.setPathParameters(Map.of("id", "ORD-001"));

        assertEquals(404, handler.handleRequest(get, null).getStatusCode());
    }

    @Test
    void answers404WhenDeletingAnOrderThatDoesNotExist() throws IOException {
        // The captured event asks for ORD-42, which is not among the preloaded orders.
        APIGatewayProxyResponseEvent response =
                handler.handleRequest(capturedEvent("delete-order.json"), null);

        assertEquals(404, response.getStatusCode());
        assertEquals("ORDER_NOT_FOUND", body(response).get("error").asText());
    }

    @Test
    void answers405ForAMethodWithNoOperation() throws IOException {
        APIGatewayProxyRequestEvent request = capturedEvent("post-order.json");
        request.setHttpMethod("PUT");

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

        assertEquals(405, response.getStatusCode());
        assertEquals("METHOD_NOT_ALLOWED", body(response).get("error").asText());
    }

    @Test
    void answers400ForAnEventItCannotRead() {
        assertEquals(400, handler.handleRequest(null, null).getStatusCode());
        assertEquals(400,
                handler.handleRequest(new APIGatewayProxyRequestEvent(), null).getStatusCode());
    }

    @Test
    void usesTheMatchedRouteAndNotTheConcretePathToDecideTheOperation() throws IOException {
        APIGatewayProxyRequestEvent request = capturedEvent("get-order-by-id.json");
        // The concrete path is irrelevant: only `resource` decides which operation runs.
        request.setPath("/this/is/ignored");
        request.setPathParameters(Map.of("id", "ORD-003"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

        assertEquals(200, response.getStatusCode());
        assertEquals("Keyboard", body(response).get("product").asText());
    }

    @Test
    void answers500AndHidesTheDetailsWhenSomethingUnexpectedFails() throws IOException {
        // An application that breaks in a way nobody planned for. The adapter must not let the
        // failure reach the client, and must not turn it into a 400 either.
        OrderService broken = new OrderService(new InMemoryOrderRepository(),
                new SequentialOrderIdGenerator()) {
            @Override
            public Order getOrder(String id) {
                throw new IllegalStateException("connection pool exhausted at ConnectionPool:412");
            }
        };

        APIGatewayProxyResponseEvent response = new OrdersApiProxyHandler(broken)
                .handleRequest(capturedEvent("get-order-by-id.json"), null);

        assertEquals(500, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", body(response).get("error").asText());
        assertEquals("The request could not be processed", body(response).get("message").asText());
        assertTrue(response.getBody().indexOf("connection pool") < 0);
        assertTrue(response.getBody().indexOf("ConnectionPool") < 0);
    }

    @Test
    void everyErrorUsesTheFormatPublishedByTheContract() throws IOException {
        List<APIGatewayProxyResponseEvent> errors = List.of(
                handler.handleRequest(capturedEvent("get-order-by-id.json"), null),
                handler.handleRequest(capturedEvent("delete-order.json"), null),
                handler.handleRequest(null, null));

        for (APIGatewayProxyResponseEvent error : errors) {
            JsonNode document = body(error);
            assertEquals(2, document.size());
            assertNotNull(document.get("error"));
            assertNotNull(document.get("message"));
            assertNull(document.get("stackTrace"));
        }
    }
}
