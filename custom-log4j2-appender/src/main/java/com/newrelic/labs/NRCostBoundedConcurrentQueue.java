package com.newrelic.labs;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class NRCostBoundedConcurrentQueue<T> {

    public interface CostAssigner<T> {
	long cost(T e);
    }

    private final LinkedBlockingQueue<T> queue;
    private final CostAssigner<T> costAssigner;
    private final long capacity;
    private final AtomicLong cost = new AtomicLong(0);

    public NRCostBoundedConcurrentQueue(long capacity, CostAssigner<T> costAssigner) {
	this.queue = new LinkedBlockingQueue<>();
	this.costAssigner = costAssigner;
	this.capacity = capacity;
    }

    public long cost() {
	return cost.get();
    }

    public int size() {
	return queue.size();
    }

    public int drainTo(Collection<T> collection, int atMost) {
	assert collection.isEmpty();
	int elementsDrained = queue.drainTo(collection, atMost);
	for (T e : collection) {
	    cost.addAndGet(-costAssigner.cost(e));
	}
	return elementsDrained;
    }

    public boolean offer(T e) {
	long eCost = costAssigner.cost(e);
	synchronized (this) {
	    if (eCost + cost.get() > capacity) {
		return false;
	    } else {
		cost.addAndGet(eCost);
	    }
	}
	return queue.add(e);
    }

    public T poll() {
	T e = queue.poll();
	if (e != null) {
	    cost.addAndGet(-costAssigner.cost(e));
	}
	return e;
    }

    /**
     * Removes all elements from the queue.
     */
    public synchronized void clear() {
	queue.clear();
	cost.set(0); // Reset the cost to zero
    }
}
