package com.escuelaing.orders.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escuelaing.orders.exception.InvalidOrderException;
import com.escuelaing.orders.exception.OrderNotFoundException;
import com.escuelaing.orders.model.CreateOrderRequest;
import com.escuelaing.orders.model.Order;
import com.escuelaing.orders.repository.InMemoryOrderRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests of the application logic. No AWS, no HTTP, no web server: the service is created with a
 * clean in-memory repository and a deterministic id generator, so every id can be asserted.
 */
class OrderServiceTest {

    private InMemoryOrderRepository repository;
    private OrderService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepository();
        service = new OrderService(repository, new SequentialOrderIdGenerator(1));
    }

    @Test
    void createsAValidOrderAndAssignsTheId() {
        Order created = service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));

        assertEquals("ORD-001", created.getId());
        assertEquals("CUST-42", created.getCustomerId());
        assertEquals("MacBook Air", created.getProduct());
        assertEquals(1, created.getQuantity());
    }

    @Test
    void storesTheCreatedOrderSoItCanBeRetrieved() {
        Order created = service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));

        assertEquals(created, service.getOrder(created.getId()));
    }

    @Test
    void assignsADifferentIdToEachOrder() {
        String first = service.createOrder(new CreateOrderRequest("CUST-1", "Mouse", 1)).getId();
        String second = service.createOrder(new CreateOrderRequest("CUST-1", "Mouse", 1)).getId();

        assertEquals("ORD-001", first);
        assertEquals("ORD-002", second);
    }

    @Test
    void ignoresAnyIdTheClientMightTryToChoose() {
        // CreateOrderRequest has no id field at all, so the id always comes from the generator.
        OrderService fixedIds = new OrderService(repository, () -> "ORD-999");

        assertEquals("ORD-999",
                fixedIds.createOrder(new CreateOrderRequest("CUST-1", "Mouse", 1)).getId());
    }

    @Test
    void trimsSurroundingWhitespaceOnCreate() {
        Order created = service.createOrder(new CreateOrderRequest("  CUST-42 ", " Keyboard ", 2));

        assertEquals("CUST-42", created.getCustomerId());
        assertEquals("Keyboard", created.getProduct());
    }

    @Test
    void retrievesAnExistingOrder() {
        repository.save(new Order("ORD-42", "CUST-7", "Keyboard", 2));

        Order found = service.getOrder("ORD-42");

        assertEquals(new Order("ORD-42", "CUST-7", "Keyboard", 2), found);
    }

    @Test
    void failsWithOrderNotFoundWhenTheOrderDoesNotExist() {
        OrderNotFoundException error =
                assertThrows(OrderNotFoundException.class, () -> service.getOrder("ORD-999"));

        assertEquals("ORDER_NOT_FOUND", error.getErrorCode());
        assertEquals("Order ORD-999 does not exist", error.getMessage());
    }

    @Test
    void findsTheOrdersOfACustomerSortedById() {
        repository.save(new Order("ORD-002", "CUST-42", "Adapter", 3));
        repository.save(new Order("ORD-001", "CUST-42", "MacBook Air", 1));
        repository.save(new Order("ORD-003", "CUST-7", "Keyboard", 2));

        List<Order> found = service.getOrdersByCustomer("CUST-42");

        assertEquals(List.of("ORD-001", "ORD-002"), found.stream().map(Order::getId).toList());
    }

    @Test
    void returnsAnEmptyListWhenTheCustomerHasNoOrders() {
        // An empty result is not an error: the collection exists, it just has no match.
        assertTrue(service.getOrdersByCustomer("CUST-100").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankOrderId(String id) {
        assertEquals("INVALID_ORDER",
                assertThrows(InvalidOrderException.class, () -> service.getOrder(id))
                        .getErrorCode());
    }

    @Test
    void rejectsANullOrderId() {
        assertThrows(InvalidOrderException.class, () -> service.getOrder(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankCustomerId(String customerId) {
        InvalidOrderException error = assertThrows(InvalidOrderException.class,
                () -> service.getOrdersByCustomer(customerId));

        assertEquals("Query parameter 'customerId' is required", error.getMessage());
    }

    @Test
    void rejectsANullCustomerId() {
        assertThrows(InvalidOrderException.class, () -> service.getOrdersByCustomer(null));
    }

    @Test
    void rejectsAMissingRequestBody() {
        InvalidOrderException error =
                assertThrows(InvalidOrderException.class, () -> service.createOrder(null));

        assertEquals("A request body is required", error.getMessage());
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
        "null,       MacBook Air, 1, Field 'customerId' is required",
        "'',         MacBook Air, 1, Field 'customerId' is required",
        "'   ',      MacBook Air, 1, Field 'customerId' is required",
        "CUST-42,    null,        1, Field 'product' is required",
        "CUST-42,    '',          1, Field 'product' is required",
        "CUST-42,    MacBook Air, null, Field 'quantity' is required",
        "CUST-42,    MacBook Air, 0, Field 'quantity' must be greater than zero",
        "CUST-42,    MacBook Air, -3, Field 'quantity' must be greater than zero",
    })
    void rejectsInvalidInput(String customerId, String product, Integer quantity, String message) {
        CreateOrderRequest request = new CreateOrderRequest(customerId, product, quantity);

        InvalidOrderException error =
                assertThrows(InvalidOrderException.class, () -> service.createOrder(request));

        assertEquals("INVALID_ORDER", error.getErrorCode());
        assertEquals(message, error.getMessage());
    }

    @Test
    void doesNotStoreAnythingWhenTheInputIsInvalid() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-42", "Mouse", 0)));

        assertTrue(repository.findByCustomerId("CUST-42").isEmpty());
    }

    @Test
    void readsTheOrdersThatWerePreloadedInTheRepository() {
        OrderService seeded = OrderService.withDefaults();

        Order sample = seeded.getOrder("ORD-001");

        assertEquals("CUST-42", sample.getCustomerId());
        assertEquals(2, seeded.getOrdersByCustomer("CUST-42").size());
        assertSame(sample, seeded.getOrder("ORD-001"));
    }
}
