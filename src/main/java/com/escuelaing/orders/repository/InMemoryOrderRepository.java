package com.escuelaing.orders.repository;

import com.escuelaing.orders.model.Order;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orders held in the memory of the process.
 *
 * <p>This is not persistent storage. A Lambda execution environment is ephemeral, so anything
 * stored here disappears when the environment is recycled and is not shared between concurrent
 * environments. It is enough for the workshop, where no database is required.
 *
 * <p>A {@link ConcurrentHashMap} is used because a single Lambda container can serve requests on
 * more than one thread.
 */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, Order> ordersById = new ConcurrentHashMap<>();

    /**
     * Repository preloaded with sample orders, so that a freshly started Lambda can already answer
     * a GET without a POST happening first in the same execution environment.
     */
    public static InMemoryOrderRepository withSampleData() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        repository.save(new Order("ORD-001", "CUST-42", "MacBook Air", 1));
        repository.save(new Order("ORD-002", "CUST-42", "USB-C Adapter", 3));
        repository.save(new Order("ORD-003", "CUST-7", "Keyboard", 2));
        return repository;
    }

    @Override
    public void save(Order order) {
        ordersById.put(order.getId(), order);
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(ordersById.get(id));
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        List<Order> matches = new ArrayList<>();
        for (Order order : ordersById.values()) {
            if (order.getCustomerId().equals(customerId)) {
                matches.add(order);
            }
        }
        matches.sort((left, right) -> left.getId().compareTo(right.getId()));
        return matches;
    }
}
