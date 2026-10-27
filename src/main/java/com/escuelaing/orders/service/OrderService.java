package com.escuelaing.orders.service;

import com.escuelaing.orders.exception.InvalidOrderException;
import com.escuelaing.orders.exception.OrderNotFoundException;
import com.escuelaing.orders.model.CreateOrderRequest;
import com.escuelaing.orders.model.Order;
import com.escuelaing.orders.repository.InMemoryOrderRepository;
import com.escuelaing.orders.repository.OrderRepository;
import java.util.List;

/**
 * Application logic of the Orders API.
 *
 * <p>This class is ordinary Java. It knows nothing about HTTP, API Gateway, Lambda, servlets or
 * Spring: it receives and returns Java objects and reports failures with exceptions. Translating
 * those concepts into HTTP status codes and JSON bodies happens outside, in the adapter layer.
 */
public class OrderService {

    private final OrderRepository repository;
    private final OrderIdGenerator idGenerator;

    public OrderService(OrderRepository repository, OrderIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    /** Service wired with the defaults used when running inside Lambda. */
    public static OrderService withDefaults() {
        return new OrderService(InMemoryOrderRepository.withSampleData(),
                new SequentialOrderIdGenerator(100));
    }

    /**
     * Validates the request, assigns an id and stores the new order.
     *
     * @throws InvalidOrderException if the request is missing data or the quantity is not positive
     */
    public Order createOrder(CreateOrderRequest request) {
        validate(request);
        Order order = new Order(idGenerator.nextId(),
                request.getCustomerId().trim(),
                request.getProduct().trim(),
                request.getQuantity());
        repository.save(order);
        return order;
    }

    /**
     * @throws InvalidOrderException if no id was supplied
     * @throws OrderNotFoundException if no order has that id
     */
    public Order getOrder(String id) {
        if (isBlank(id)) {
            throw new InvalidOrderException("Field 'id' is required");
        }
        return repository.findById(id.trim())
                .orElseThrow(() -> new OrderNotFoundException(id.trim()));
    }

    /**
     * Orders of one customer, an empty list when the customer has none. An empty result is not an
     * error: the collection exists, it simply has no matches.
     *
     * @throws InvalidOrderException if no customer id was supplied
     */
    public List<Order> getOrdersByCustomer(String customerId) {
        if (isBlank(customerId)) {
            throw new InvalidOrderException("Query parameter 'customerId' is required");
        }
        return repository.findByCustomerId(customerId.trim());
    }

    /**
     * Removes an order.
     *
     * <p>Deleting an order that is not there is reported as a failure rather than silently
     * accepted: whether the order exists is a question about the domain, and answering it here is
     * what lets the HTTP layer choose between 204 and 404 without asking the repository itself.
     *
     * @throws InvalidOrderException if no id was supplied
     * @throws OrderNotFoundException if no order has that id
     */
    public void deleteOrder(String id) {
        if (isBlank(id)) {
            throw new InvalidOrderException("Field 'id' is required");
        }
        if (!repository.deleteById(id.trim())) {
            throw new OrderNotFoundException(id.trim());
        }
    }

    private void validate(CreateOrderRequest request) {
        if (request == null) {
            throw new InvalidOrderException("A request body is required");
        }
        if (isBlank(request.getCustomerId())) {
            throw new InvalidOrderException("Field 'customerId' is required");
        }
        if (isBlank(request.getProduct())) {
            throw new InvalidOrderException("Field 'product' is required");
        }
        if (request.getQuantity() == null) {
            throw new InvalidOrderException("Field 'quantity' is required");
        }
        if (request.getQuantity() <= 0) {
            throw new InvalidOrderException("Field 'quantity' must be greater than zero");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
