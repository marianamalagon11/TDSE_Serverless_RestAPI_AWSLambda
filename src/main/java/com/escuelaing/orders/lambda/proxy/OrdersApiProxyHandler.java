package com.escuelaing.orders.lambda.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.escuelaing.orders.exception.InvalidOrderException;
import com.escuelaing.orders.exception.OrderNotFoundException;
import com.escuelaing.orders.model.CreateOrderRequest;
import com.escuelaing.orders.model.Order;
import com.escuelaing.orders.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/**
 * The whole Orders API behind a single Lambda function, using the API Gateway proxy integration.
 *
 * <p>This class is the adapter between HTTP and the application. It is allowed to know about
 * methods, routes, parameters, status codes and headers, and it is the only class in the project
 * that does. {@link OrderService} on the other side of it still speaks only of orders, customers
 * and quantities, and is not aware that any of this exists.
 *
 * <p>Compared with the custom integration of section 6, the responsibilities that moved into this
 * file are routing, parameter extraction, body parsing, status codes, the error format and the
 * {@code Location} header. What they have in common is that all of them are now written once, in
 * code that compiles and that is covered by tests running on a laptop.
 */
public class OrdersApiProxyHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Built once per execution environment, so a warm invocation reuses it. That is what Lambda
     * wants and what a test does not: a static service would carry the orders created by one test
     * into the next one.
     */
    private static final OrderService SHARED_SERVICE = OrderService.withDefaults();

    private final OrderService service;

    /** The constructor the Lambda runtime uses. */
    public OrdersApiProxyHandler() {
        this(SHARED_SERVICE);
    }

    /** Lets a test run against its own, freshly seeded application. */
    OrdersApiProxyHandler(OrderService service) {
        this.service = service;
    }

    private static final String ORDERS = "/orders";
    private static final String ORDER_BY_ID = "/orders/{id}";

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request,
            Context context) {
        try {
            return route(request);
        } catch (OrderNotFoundException notFound) {
            return HttpResponses.error(404, notFound.getErrorCode(), notFound.getMessage());
        } catch (InvalidOrderException invalid) {
            return HttpResponses.error(400, invalid.getErrorCode(), invalid.getMessage());
        } catch (MalformedBodyException malformed) {
            return HttpResponses.error(400, "MALFORMED_JSON", malformed.getMessage());
        } catch (RuntimeException unexpected) {
            // Logged by the runtime to CloudWatch, never shown to the client.
            if (context != null) {
                context.getLogger().log("Unexpected failure: " + unexpected);
            }
            return HttpResponses.error(500, "INTERNAL_ERROR",
                    "The request could not be processed");
        }
    }

    /**
     * Chooses the operation from the route that matched and the HTTP method.
     *
     * <p>The decision is made on {@code resource} and not on {@code path}: {@code resource} is the
     * route API Gateway already matched, {@code /orders/{id}}, while {@code path} is the concrete
     * URL and would have to be parsed again.
     */
    private APIGatewayProxyResponseEvent route(APIGatewayProxyRequestEvent request) {
        if (request == null || request.getResource() == null || request.getHttpMethod() == null) {
            return HttpResponses.error(400, "INVALID_REQUEST", "The request could not be read");
        }

        String resource = request.getResource();
        String method = request.getHttpMethod().toUpperCase();

        if (ORDERS.equals(resource) && "POST".equals(method)) {
            String mediaType = header(request, "Content-Type");
            // Absent is tolerated, present and not JSON is not. In the custom integration of
            // section 6 API Gateway answered this on its own; here it has to be written down.
            if (mediaType != null && !mediaType.toLowerCase().startsWith("application/json")) {
                return HttpResponses.error(415, "UNSUPPORTED_MEDIA_TYPE",
                        "The request body must be application/json");
            }
            return createOrder(request);
        }
        if (ORDERS.equals(resource) && "GET".equals(method)) {
            return findOrdersByCustomer(request);
        }
        if (ORDER_BY_ID.equals(resource) && "GET".equals(method)) {
            return getOrder(request);
        }

        return HttpResponses.error(405, "METHOD_NOT_ALLOWED",
                method + " is not supported on " + resource);
    }

    private APIGatewayProxyResponseEvent createOrder(APIGatewayProxyRequestEvent request) {
        CreateOrderRequest body = parseBody(request.getBody());
        Order created = service.createOrder(body);
        return HttpResponses.created(created, locationOf(request, created.getId()));
    }

    private APIGatewayProxyResponseEvent getOrder(APIGatewayProxyRequestEvent request) {
        Order found = service.getOrder(pathParameter(request, "id"));
        return HttpResponses.ok(found);
    }

    private APIGatewayProxyResponseEvent findOrdersByCustomer(APIGatewayProxyRequestEvent request) {
        List<Order> found = service.getOrdersByCustomer(queryParameter(request, "customerId"));
        return HttpResponses.ok(found);
    }

    /**
     * API Gateway hands the body over as a string and never looks inside it, so parsing it, and
     * failing cleanly when it is not JSON at all, is the responsibility of this layer.
     */
    private CreateOrderRequest parseBody(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidOrderException("A request body is required");
        }
        try {
            return JSON.readValue(body, CreateOrderRequest.class);
        } catch (JsonProcessingException notJson) {
            throw new MalformedBodyException("The request body is not valid JSON");
        }
    }

    /** Absent maps arrive as {@code null}, not as empty maps, as the captured events show. */
    private String pathParameter(APIGatewayProxyRequestEvent request, String name) {
        Map<String, String> parameters = request.getPathParameters();
        return parameters == null ? null : parameters.get(name);
    }

    private String queryParameter(APIGatewayProxyRequestEvent request, String name) {
        Map<String, String> parameters = request.getQueryStringParameters();
        return parameters == null ? null : parameters.get(name);
    }

    /** Header names are case insensitive in HTTP, but the event delivers them in a plain map. */
    private String header(APIGatewayProxyRequestEvent request, String name) {
        Map<String, String> headers = request.getHeaders();
        if (headers == null) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(header -> header.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Where the created order can be read, as required of a {@code 201 Created}. It is built from
     * the path of the request itself, so it stays correct whatever stage or domain the API is
     * deployed under.
     */
    private String locationOf(APIGatewayProxyRequestEvent request, String orderId) {
        String basePath = request.getRequestContext() == null
                ? null : request.getRequestContext().getPath();
        return (basePath == null ? ORDERS : basePath) + "/" + orderId;
    }

    /** The body was present but could not be read as JSON, which is a client error. */
    static class MalformedBodyException extends RuntimeException {
        MalformedBodyException(String message) {
            super(message);
        }
    }
}
