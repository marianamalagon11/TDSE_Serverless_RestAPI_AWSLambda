package com.escuelaing.orders.repository;

import com.escuelaing.orders.model.Order;
import java.util.List;
import java.util.Optional;

/**
 * Storage port of the application.
 *
 * <p>The service depends on this interface and not on a concrete store, so the orders can live in
 * a map today and in a database later without the business logic noticing.
 */
public interface OrderRepository {

    /** Stores the order, replacing any previous order with the same id. */
    void save(Order order);

    Optional<Order> findById(String id);

    List<Order> findByCustomerId(String customerId);
}
