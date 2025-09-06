package org.srj.concurrency.blockingqueueimpl;

import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        // Create a thread-safe, bounded queue with a capacity of 5.
        BlockingBoundedQueue<Integer> queue = new BlockingBoundedQueue<Integer>(5);

        // Producer thread that adds items to the queue
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    System.out.println("Producer is trying to put: " + i);
                    queue.put(i); // This will block if the queue is full
                    System.out.println("Producer successfully put: " + i + " [Queue size: " + queue.size() + "]");
                    TimeUnit.MILLISECONDS.sleep(200); // Simulate work
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Consumer thread that takes items from the queue
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    System.out.println("Consumer is waiting to take...");
                    Integer item = queue.take(); // This will block if the queue is empty
                    System.out.println("Consumer took: " + item + " [Queue size: " + queue.size() + "]");
                    TimeUnit.MILLISECONDS.sleep(500); // Simulate work
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();
    }
}