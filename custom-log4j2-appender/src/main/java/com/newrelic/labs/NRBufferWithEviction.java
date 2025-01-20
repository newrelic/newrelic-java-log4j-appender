package com.newrelic.labs;

import java.util.Collection;

/**
 * A concurrent buffer with a maximum capacity that, upon reaching said capacity, evicts some
 * elements in the queue to ensure the new element can fit.
 */
public abstract class NRBufferWithEviction<Q> {

  private long capacity;

  public NRBufferWithEviction(long capacity) {
    this.capacity = capacity;
  }

  /**
   * Adds an element to the buffer.
   *
   * @param element The element to add.
   * @return True if the element was added successfully, false otherwise.
   */
  public abstract boolean add(Q element);

  /**
   * Drains elements from the buffer into the given collection.
   *
   * @param collection The collection to drain elements into.
   * @param atMost The maximum number of elements to drain.
   * @return The number of elements drained.
   */
  public abstract int drainTo(Collection<Q> collection, int atMost);

  /**
   * Evicts an element from the buffer to make room for new elements.
   *
   * @return The evicted element.
   */
  protected abstract Q evict();

  /**
   * Evicts elements from the buffer to make room for an element with the specified cost.
   *
   * @param cost The cost of the element to be accommodated.
   * @return True if eviction was successful, false otherwise.
   */
  protected abstract boolean evict(long cost);

  public long getCapacity() {
    return capacity;
  }

  public void setCapacity(long capacity) {
    this.capacity = capacity;
  }

  /**
   * Returns the number of elements in the buffer.
   *
   * @return The size of the buffer.
   */
  public abstract int size();
}
