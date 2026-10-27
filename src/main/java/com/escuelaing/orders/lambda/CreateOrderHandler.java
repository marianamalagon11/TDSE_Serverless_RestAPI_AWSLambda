package com.escuelaing.orders.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.escuelaing.orders.model.CreateOrderRequest;
import com.escuelaing.orders.model.Order;
import com.escuelaing.orders.service.OrderService;

/**
 * Exposes the "create an order" operation of {@link OrderService} as an AWS Lambda function.
 *
 * <p>This is the whole AWS-specific surface of the operation: it implements the runtime interface
 * and does nothing else. It does not validate, it does not build ids and it does not decide HTTP
 * status codes, because none of that belongs here.
 *
 * <p>The runtime does the JSON work on both sides. It deserializes the invocation payload into a
 * {@link CreateOrderRequest} and serializes the returned {@link Order} back to JSON, which is why
 * this class never mentions Jackson, strings or streams.
 *
 * <p>The service is held in a static field on purpose: fields initialized once per execution
 * environment survive warm invocations, so the in-memory data created by one request is still
 * visible to the next request served by the same container, and only that container.
 */
public class CreateOrderHandler implements RequestHandler<CreateOrderRequest, Order> {

    private static final OrderService SERVICE = OrderService.withDefaults();

    @Override
    public Order handleRequest(CreateOrderRequest request, Context context) {
        return SERVICE.createOrder(request);
    }
}
