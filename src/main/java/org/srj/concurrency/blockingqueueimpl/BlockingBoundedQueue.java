package org.srj.concurrency.blockingqueueimpl;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A custom implementation of a blocking bounded queue.
 * @param <E> the type of elements held in this queue
 */
public class BlockingBoundedQueue<E> {

    private final Queue<E> queue;
    private final int capacity;
    private final Lock lock = new ReentrantLock();

    // Condition for waiting when the queue is full
    private final Condition notFull = lock.newCondition();

    // Condition for waiting when the queue is empty
    private final Condition notEmpty = lock.newCondition();

    public BlockingBoundedQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive.");
        }
        this.queue = new LinkedList<>();
        this.capacity = capacity;
    }

    /**
     * Inserts the specified element into the queue, waiting if necessary for space to become available.
     * @param element the element to add
     * @throws InterruptedException if interrupted while waiting
     */
    public void put(E element) throws InterruptedException {
        lock.lock(); // Acquire the lock
        try {
            // Use a 'while' loop to handle spurious wakeups
            while (queue.size() == capacity) {
                System.out.println("Queue is full. Producer is waiting...");
                notFull.await(); // Block until not full
            }
            queue.add(element);
            System.out.println("Produced: " + element);
            notEmpty.signal(); // Signal one waiting consumer that the queue is no longer empty
        } finally {
            lock.unlock(); // Always release the lock
        }
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary until an element becomes available.
     * @return the head of this queue
     * @throws InterruptedException if interrupted while waiting
     */
    public E take() throws InterruptedException {
        lock.lock(); // Acquire the lock
        try {
            // Use a 'while' loop to handle spurious wakeups
            while (queue.isEmpty()) {
                System.out.println("Queue is empty. Consumer is waiting...");
                notEmpty.await(); // Block until not empty
            }
            E element = queue.poll();
            System.out.println("Consumed: " + element);
            notFull.signal(); // Signal one waiting producer that the queue is no longer full
            return element;
        } finally {
            lock.unlock(); // Always release the lock
        }
    }

    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }
}