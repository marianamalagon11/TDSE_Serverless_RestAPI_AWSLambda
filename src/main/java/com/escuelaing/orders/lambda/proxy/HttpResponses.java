package com.escuelaing.orders.lambda.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the HTTP responses of the API.
 *
 * <p>Every status code the API can answer is written here, in Java, and nowhere else. In the
 * custom integration of section 6 these same decisions lived in API Gateway configuration, split
 * between method responses, integration responses and selection patterns.
 */
final class HttpResponses {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpResponses() {
    }

    static APIGatewayProxyResponseEvent ok(Object body) {
        return json(200, body, null);
    }

    /** {@code 201 Created} pointing at the resource that was just created. */
    static APIGatewayProxyResponseEvent created(Object body, String location) {
        return json(201, body, location);
    }

    static APIGatewayProxyResponseEvent noContent() {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(204);
        response.setHeaders(Map.of());
        response.setBody(null);
        return response;
    }

    /** An error in the format published by the API contract. */
    static APIGatewayProxyResponseEvent error(int statusCode, String error, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        return json(statusCode, body, null);
    }

    private static APIGatewayProxyResponseEvent json(int statusCode, Object body, String location) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (location != null) {
            headers.put("Location", location);
        }

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setHeaders(headers);
        response.setBody(serialize(body));
        return response;
    }

    private static String serialize(Object body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException impossible) {
            // Only the application's own model objects are serialized here.
            throw new IllegalStateException("Could not serialize the response body", impossible);
        }
    }
}
