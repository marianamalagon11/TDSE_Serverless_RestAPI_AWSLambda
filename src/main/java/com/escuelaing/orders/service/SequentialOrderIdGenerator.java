package com.escuelaing.orders.service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates ids of the form {@code ORD-001}, {@code ORD-002}, and so on.
 *
 * <p>The counter lives in the memory of the process, exactly like the in-memory repository. On AWS
 * Lambda that means it restarts on every cold start and is not shared between execution
 * environments, so two orders created in different environments could receive the same id. Real
 * persistence would move id generation to the database or use a UUID instead.
 */
public class SequentialOrderIdGenerator implements OrderIdGenerator {

    private final AtomicInteger counter;

    public SequentialOrderIdGenerator() {
        this(1);
    }

    public SequentialOrderIdGenerator(int firstValue) {
        this.counter = new AtomicInteger(firstValue);
    }

    @Override
    public String nextId() {
        return String.format("ORD-%03d", counter.getAndIncrement());
    }
}
