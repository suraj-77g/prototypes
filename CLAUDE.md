# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

**Build:**
```bash
mvn clean compile
```

**Run demos:**
```bash
# Zero-copy performance demo
mvn exec:java -Dexec.mainClass="zerocopy.ZeroCopyDemo"

# Blocking queue producer-consumer demo
mvn exec:java -Dexec.mainClass="org.srj.concurrency.blockingqueueimpl.Main"

# I/O Models demos
mvn exec:java -Dexec.mainClass="iomodels.blocking.BlockingIOServer"
mvn exec:java -Dexec.mainClass="iomodels.nio.NioWorkerPoolServer"
mvn exec:java -Dexec.mainClass="iomodels.eventloop.EventLoopServer"
```

No test suite exists yet. Java 17 is required.

## Architecture

This is a Java prototype/learning repository (Maven, group `org.srj`) with two independent modules under `src/main/java/`:

### `zerocopy/`
Benchmarks traditional vs. zero-copy file transfer. Creates a 200 MiB temp file, copies it both ways, prints timing, then cleans up. Zero-copy uses `FileChannel.transferTo()` to keep data in kernel space (no user-space buffer round-trips). Motivated by how Kafka achieves high throughput.

### `org/srj/concurrency/blockingqueueimpl/`
Custom generic `BlockingBoundedQueue<E>` backed by a `LinkedList`, synchronized with a single `ReentrantLock` and two `Condition`s (`notFull`, `notEmpty`). `Main.java` drives a producer (200 ms/item) against a consumer (500 ms/item) into a capacity-5 queue to observe backpressure.

### `iomodels/`
Three self-contained demos comparing Java I/O concurrency models. See `iomodels/README.md` for architecture comparisons and system design guidance.
- `blocking/BlockingIOServer.java` — ServerSocket + thread pool; one thread per connection
- `nio/NioWorkerPoolServer.java` — Selector IO thread + ExecutorService worker pool
- `eventloop/EventLoopServer.java` — Single-thread Selector; slow handler visibly starves all connections
