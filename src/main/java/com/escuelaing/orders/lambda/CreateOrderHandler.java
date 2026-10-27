package com.escuelaing.orders.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.escuelaing.orders.exception.OrderException;
import com.escuelaing.orders.model.CreateOrderRequest;
import com.escuelaing.orders.model.Order;
import com.escuelaing.orders.service.OrderService;

/**
 * Exposes the "create an order" operation of {@link OrderService} as an AWS Lambda function.
 *
 * <p>The runtime does the JSON work on both sides: it deserializes the invocation payload into a
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
        try {
            return SERVICE.createOrder(request);
        } catch (OrderException failure) {
            throw FunctionErrorException.from(failure);
        } catch (RuntimeException unexpected) {
            throw FunctionErrorException.internal(unexpected);
        }
    }
}
