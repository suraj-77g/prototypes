# Zero Copy prototype
The zero-copy principle is a computer operation where the CPU moves data from one memory location to another without performing a redundant copy of the data into the user-level software's memory buffer. It allows the operating system's kernel to transfer data directly from a source (like a disk) to a destination (like a network card) without the application's intervention.
This avoids multiple, unnecessary data copies and context switches between user space and kernel space, significantly improving the performance of I/O-heavy applications.

#### How Kafka Benefits from Zero-Copy
Kafka is a high-throughput, distributed streaming platform. Its primary job is to move vast amounts of log data from producers to disk and then efficiently serve that data from disk to consumers. The most common data path in Kafka is reading messages from the broker's disk log and writing them to a consumer's network socket.

This "disk-to-network" scenario is the perfect use case for zero-copy.

* High Throughput: By using the sendfile() system call (exposed in Java via FileChannel.transferTo()), Kafka brokers can send message data directly from the filesystem cache to the network card. This avoids loading the data into the Kafka application's memory (the JVM heap).
* Lower CPU Utilization: Since the CPU is not involved in copying data between kernel and user buffers, it is free to perform other tasks, such as processing more incoming messages.
* Reduced Garbage Collection (GC) Pressure: Because the message data doesn't clutter the JVM heap, there is significantly less pressure on the garbage collector. This leads to more predictable and lower latency, which is critical for a real-time system like Kafka.
* In essence, zero-copy is a cornerstone of Kafka's performance, allowing a single broker to saturate a network link by serving terabytes of data to consumers with minimal CPU and memory overhead.

#### Java Code Prototype: Zero-Copy in Action
* Here's a simple Java program that demonstrates the performance difference. We'll create a 200 MiB file and then use both a traditional copy method and a zero-copy method to see which is faster.
* The zero-copy implementation in Java uses the transferTo() method from the java.nio.channels.FileChannel class.