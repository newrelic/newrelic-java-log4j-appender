package com.newrelic.labs;

import java.util.Collection;

import com.newrelic.labs.NRCostBoundedConcurrentQueue.CostAssigner;

public class NRBufferWithFifoEviction<T> extends NRBufferWithEviction<T> {

    private final NRCostBoundedConcurrentQueue<T> queue;
    private final CostAssigner<T> costAssigner;

    public NRBufferWithFifoEviction(long capacity, CostAssigner<T> costAssigner) {
	super(capacity);
	this.queue = new NRCostBoundedConcurrentQueue<>(capacity, costAssigner);
	this.costAssigner = costAssigner;
    }

    @Override
    protected T evict() {
	return queue.poll();
    }

    @Override
    protected boolean evict(long cost) {
	if (cost > getCapacity())
	    return false;
	long targetCost = getCapacity() - cost;
	while (queue.cost() > targetCost) {
	    evict();
	}
	return true;
    }

    @Override
    public int size() {
	return queue.size();
    }

    @Override
    public int drainTo(Collection<T> collection, int atMost) {
	return queue.drainTo(collection, atMost);
    }

    @Override
    public boolean add(T element) {
	boolean wasSuccessful = queue.offer(element);
	if (!wasSuccessful) {
	    evict(costAssigner.cost(element));
	    return queue.offer(element);
	}
	return true;
    }

    /**
     * Clears all elements from the buffer.
     */
    public synchronized void clear() {
	queue.clear();
	// logger.info("Cleared all elements from the buffer");
    }
}
