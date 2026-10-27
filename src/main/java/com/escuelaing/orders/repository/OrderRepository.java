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

    /**
     * Removes the order with that id.
     *
     * @return whether an order was actually removed, so the caller can tell "deleted" from
     *     "there was nothing to delete" without asking twice
     */
    boolean deleteById(String id);
}
