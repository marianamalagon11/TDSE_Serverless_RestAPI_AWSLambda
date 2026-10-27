package com.escuelaing.orders.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SequentialOrderIdGeneratorTest {

    @Test
    void startsAtOneAndPadsToThreeDigits() {
        OrderIdGenerator generator = new SequentialOrderIdGenerator();

        assertEquals("ORD-001", generator.nextId());
        assertEquals("ORD-002", generator.nextId());
    }

    @Test
    void canStartAtAGivenValue() {
        OrderIdGenerator generator = new SequentialOrderIdGenerator(100);

        assertEquals("ORD-100", generator.nextId());
        assertEquals("ORD-101", generator.nextId());
    }

    @Test
    void keepsGrowingBeyondThreeDigits() {
        OrderIdGenerator generator = new SequentialOrderIdGenerator(999);

        assertEquals("ORD-999", generator.nextId());
        assertEquals("ORD-1000", generator.nextId());
    }
}
