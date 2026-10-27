package com.escuelaing.orders.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.escuelaing.orders.exception.OrderException;
import com.escuelaing.orders.model.DeleteOrderRequest;
import com.escuelaing.orders.service.OrderService;

/**
 * Exposes the "delete an order" operation of {@link OrderService} as an AWS Lambda function.
 *
 * <p>There is nothing to return when a deletion succeeds, which is awkward here: the runtime still
 * serializes the result, and {@code null} becomes the four characters {@code null} in the response
 * payload. Keeping that out of the body of a {@code 204} is the job of the integration response in
 * API Gateway, not of this class.
 */
public class DeleteOrderHandler implements RequestHandler<DeleteOrderRequest, Void> {

    private static final OrderService SERVICE = OrderService.withDefaults();

    @Override
    public Void handleRequest(DeleteOrderRequest request, Context context) {
        try {
            SERVICE.deleteOrder(request == null ? null : request.getId());
            return null;
        } catch (OrderException failure) {
            throw FunctionErrorException.from(failure);
        } catch (RuntimeException unexpected) {
            throw FunctionErrorException.internal(unexpected);
        }
    }
}
