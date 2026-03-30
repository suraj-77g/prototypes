# I/O Models: Blocking vs NIO+Pool vs Event Loop

Three self-contained echo-server demos illustrating the three fundamental server-side I/O concurrency models in Java.

---

## Overview

| Demo | Model | Thread model | Blocking point |
|------|-------|--------------|----------------|
| `BlockingIOServer` | Classic blocking I/O | One thread per connection | `socket.read()` blocks thread |
| `NioWorkerPoolServer` | NIO Selector + worker pool | 1 IO thread + N worker threads | IO thread never blocks; workers may |
| `EventLoopServer` | Single-thread event loop | 1 thread for everything | Any blocking call stalls ALL connections |

---

## Tradeoffs

| | Blocking | NIO + Pool | Event Loop |
|---|---|---|---|
| Code simplicity | High | Medium | Medium |
| CPU-bound workloads | Good | Good | Poor (starves other conns) |
| I/O-bound workloads | Poor (threads idle) | Excellent | Excellent |
| Memory per connection | High (~1 MB stack/thread) | Low (no thread per conn) | Lowest |
| Backpressure | Natural (thread pool queue) | Explicit (pool queue) | Manual (must not block) |
| Max connections | ~500–1000 | 10k–100k+ | 10k–100k+ |

---

## Demo 1: BlockingIOServer

```
ServerSocket.accept() → pool.submit(handleConnection)
handleConnection: BufferedReader.readLine() blocks thread until data arrives
```

Watch the output — thread names like `pool-1-thread-3` map directly to connections. With 5 clients and a pool of 10, threads are plentiful. Scale to 10,000 clients and you'd need 10,000 threads (~10 GB RAM just for stacks).

---

## Demo 2: NioWorkerPoolServer

```
Selector.select() ← single IO thread watches ALL channels
  OP_ACCEPT → accept, register OP_READ
  OP_READ   → read bytes, dispatch to workerPool.submit(processRequest)
```

The `[IO Thread]` line appears for every accept/read event regardless of connection count. Worker threads (`pool-1-thread-N`) do the actual work. The IO thread is never blocked — it multiplexes all connections via the OS epoll/kqueue mechanism.

---

## Demo 3: EventLoopServer

```
Selector.select() ← single thread
  OP_ACCEPT → accept (inline)
  OP_READ   → read + process (inline, NO pool)
```

Client #2 sends `SLOW_PING`. The event loop calls `Thread.sleep(1000)` inline. **All other connections freeze** for that second — check the `Instant.now()` timestamps in the output. This is the Node.js gotcha: one synchronous/blocking handler ruins latency for every other request.

---

## Real-World Servers

### Tomcat BIO (< 8.5) — Demo 1
Classic `ServerSocket` with one thread per HTTP request. Default connector before Tomcat 8.5. Practical ceiling ~200 concurrent requests before thread/memory exhaustion. Still fine for internal microservices with low concurrency.

### Tomcat NIO (8.5+, default) — Demo 2
`NioEndpoint` with `Acceptor` thread, `Poller` thread (Selector), and `Executor` (worker pool). Poller multiplexes keep-alive connections; workers handle request processing. Can handle thousands of concurrent keep-alive connections with a modest worker pool.

### Jetty — Demo 2 variant
`SelectorManager` owns the Selector logic; `QueuedThreadPool` provides workers. Jetty's `HttpChannel` processing happens on worker threads. Architecture is structurally identical to Demo 2 but with more sophisticated connection lifecycle management.

### Nginx — Demo 3 (C, no blocking risk)
One event loop per worker process (typically one per CPU core). Uses epoll/kqueue. Because Nginx handlers are written in C and never block (disk I/O is async, upstream proxying is async), the starvation problem in Demo 3 doesn't occur. The event loop model works brilliantly when every handler is guaranteed fast.

### Node.js — Demo 3 (with escape hatch)
libuv provides the event loop. JavaScript callbacks run on the single event loop thread. CPU-intensive work (crypto, JSON parsing of large payloads, image processing) blocks the loop — exactly the `SLOW_PING` scenario. The `worker_threads` module provides a Demo-2-style escape: offload blocking work to a thread pool, keep the event loop for I/O coordination.

### Netty — Configurable Demo 2 or Demo 3
`NioEventLoopGroup bossGroup` (acceptor) + `NioEventLoopGroup workerGroup` (IO). By default, Netty handlers run on the IO event loop thread (Demo 3 model). Adding a `DefaultEventExecutorGroup` to a `ChannelPipeline` offloads handlers to a worker pool (Demo 2 model). Netty's design explicitly lets you choose based on whether your handlers are CPU-bound.

### Vert.x — Demo 3 + explicit offload
JVM port of the Node.js model. Verticles run on event loop threads. `vertx.executeBlocking()` dispatches to a worker pool — the Vert.x-idiomatic way to do what Demo 2 shows. The framework enforces the contract: if you block the event loop thread, Vert.x logs a warning.

---

## System Design Guidance

**Use Blocking I/O (Demo 1) when:**
- Connection count stays below ~500
- Workloads are CPU-bound (threads are busy, not idle)
- You want simpler code and easier debugging (thread-per-request = straightforward stack traces)
- Internal services where connection counts are controlled

**Use NIO + Worker Pool (Demo 2) when:**
- Expecting 1k–100k concurrent connections
- Workloads mix I/O waiting with real computation
- You need explicit backpressure via worker pool queue depth
- This is the **safe modern default** — Tomcat NIO, Jetty, Spring WebFlux's fallback mode

**Use Event Loop (Demo 3) when:**
- Extreme connection counts with fast, uniform handlers (pure proxies, WebSocket fan-out, API gateways)
- Every handler is guaranteed non-blocking (or you offload via `executeBlocking`/`worker_threads`)
- You're comfortable with the discipline of never blocking the loop
- Avoid if any handler can block, do significant CPU work, or call synchronous libraries
