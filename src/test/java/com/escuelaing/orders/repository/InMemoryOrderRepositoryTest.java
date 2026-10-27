package com.escuelaing.orders.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escuelaing.orders.model.Order;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryOrderRepositoryTest {

    private InMemoryOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepository();
    }

    @Test
    void savesAndFindsAnOrderById() {
        Order order = new Order("ORD-001", "CUST-42", "MacBook Air", 1);

        repository.save(order);

        assertEquals(order, repository.findById("ORD-001").orElseThrow());
    }

    @Test
    void returnsAnEmptyOptionalForAnUnknownId() {
        assertTrue(repository.findById("ORD-999").isEmpty());
    }

    @Test
    void replacesAnOrderSavedWithTheSameId() {
        repository.save(new Order("ORD-001", "CUST-42", "MacBook Air", 1));
        repository.save(new Order("ORD-001", "CUST-42", "MacBook Pro", 2));

        assertEquals("MacBook Pro", repository.findById("ORD-001").orElseThrow().getProduct());
    }

    @Test
    void findsOnlyTheOrdersOfTheRequestedCustomer() {
        repository.save(new Order("ORD-001", "CUST-42", "MacBook Air", 1));
        repository.save(new Order("ORD-002", "CUST-7", "Keyboard", 2));

        List<Order> found = repository.findByCustomerId("CUST-42");

        assertEquals(1, found.size());
        assertEquals("ORD-001", found.get(0).getId());
    }

    @Test
    void returnsTheOrdersOfACustomerSortedById() {
        repository.save(new Order("ORD-003", "CUST-42", "Mouse", 1));
        repository.save(new Order("ORD-001", "CUST-42", "MacBook Air", 1));
        repository.save(new Order("ORD-002", "CUST-42", "Adapter", 3));

        assertEquals(List.of("ORD-001", "ORD-002", "ORD-003"),
                repository.findByCustomerId("CUST-42").stream().map(Order::getId).toList());
    }

    @Test
    void deletesAnExistingOrderAndSaysSo() {
        repository.save(new Order("ORD-001", "CUST-42", "MacBook Air", 1));

        assertTrue(repository.deleteById("ORD-001"));
        assertTrue(repository.findById("ORD-001").isEmpty());
    }

    @Test
    void reportsThatNothingWasDeletedForAnUnknownId() {
        assertFalse(repository.deleteById("ORD-999"));
    }

    @Test
    void removesADeletedOrderFromTheOrdersOfItsCustomer() {
        repository.save(new Order("ORD-001", "CUST-42", "MacBook Air", 1));
        repository.save(new Order("ORD-002", "CUST-42", "Adapter", 3));

        repository.deleteById("ORD-001");

        assertEquals(List.of("ORD-002"),
                repository.findByCustomerId("CUST-42").stream().map(Order::getId).toList());
    }

    @Test
    void preloadsSampleOrders() {
        InMemoryOrderRepository seeded = InMemoryOrderRepository.withSampleData();

        assertEquals(2, seeded.findByCustomerId("CUST-42").size());
        assertEquals(1, seeded.findByCustomerId("CUST-7").size());
        assertTrue(seeded.findById("ORD-001").isPresent());
    }
}
