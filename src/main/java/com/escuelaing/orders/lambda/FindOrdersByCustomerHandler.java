package com.escuelaing.orders.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.escuelaing.orders.exception.OrderException;
import com.escuelaing.orders.model.FindOrdersByCustomerRequest;
import com.escuelaing.orders.model.Order;
import com.escuelaing.orders.service.OrderService;
import java.util.List;

/**
 * Exposes the "find orders belonging to a customer" operation as an AWS Lambda function.
 *
 * <p>The function returns a list, and the runtime serializes it as a JSON array. A customer with
 * no orders produces {@code []} and not an error, because the collection exists and simply has no
 * match.
 */
public class FindOrdersByCustomerHandler
        implements RequestHandler<FindOrdersByCustomerRequest, List<Order>> {

    private static final OrderService SERVICE = OrderService.withDefaults();

    @Override
    public List<Order> handleRequest(FindOrdersByCustomerRequest request, Context context) {
        try {
            return SERVICE.getOrdersByCustomer(request == null ? null : request.getCustomerId());
        } catch (OrderException failure) {
            throw FunctionErrorException.from(failure);
        } catch (RuntimeException unexpected) {
            throw FunctionErrorException.internal(unexpected);
        }
    }
}
