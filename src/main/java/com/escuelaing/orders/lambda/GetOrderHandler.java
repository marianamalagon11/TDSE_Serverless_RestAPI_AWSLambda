package com.escuelaing.orders.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.escuelaing.orders.model.GetOrderRequest;
import com.escuelaing.orders.model.Order;
import com.escuelaing.orders.service.OrderService;

/**
 * Exposes the "retrieve an order" operation of {@link OrderService} as an AWS Lambda function.
 *
 * <p>The function expects the identifier already extracted, as {@code {"id": "ORD-42"}}. It never
 * sees the URL {@code /orders/ORD-42}, the HTTP method or any header, because in this architecture
 * API Gateway is the one that reads the path and builds this document.
 */
public class GetOrderHandler implements RequestHandler<GetOrderRequest, Order> {

    private static final OrderService SERVICE = OrderService.withDefaults();

    @Override
    public Order handleRequest(GetOrderRequest request, Context context) {
        return SERVICE.getOrder(request == null ? null : request.getId());
    }
}
