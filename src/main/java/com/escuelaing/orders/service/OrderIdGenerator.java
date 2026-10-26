package com.escuelaing.orders.service;

/**
 * Source of order ids.
 *
 * <p>It is a separate collaborator so that the service stays deterministic under test: a test can
 * inject a generator that always returns a known id and then assert on it.
 */
public interface OrderIdGenerator {

    String nextId();
}
