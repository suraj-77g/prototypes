package iomodels.nio;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.*;

/**
 * Demo 2: NIO Selector + worker pool.
 * A single IO thread handles all accept/read events; actual processing
 * is dispatched to a fixed thread pool. Models Tomcat NIO / Jetty.
 *
 * Run: mvn exec:java -Dexec.mainClass="iomodels.nio.NioWorkerPoolServer"
 */
public class NioWorkerPoolServer {

    private static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(0));
        serverChannel.configureBlocking(false);
        int port = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
        System.out.println("[Server] NioWorkerPoolServer listening on port " + port);

        Selector selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        ExecutorService workerPool = Executors.newFixedThreadPool(4);

        // Shutdown after 5 s — needs selector.wakeup() to unblock select()
        Thread shutdown = new Thread(() -> {
            try { Thread.sleep(5_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.println("[Server] Shutting down...");
            running = false;
            selector.wakeup();
        });
        shutdown.setDaemon(true);
        shutdown.start();

        // 3 staggered clients, 1 PING each
        for (int i = 0; i < 3; i++) {
            final int id = i;
            Thread.sleep(200);
            new Thread(() -> {
                try (Socket s = new Socket("localhost", port);
                     PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    System.out.println("[Client-" + id + "] → PING");
                    out.println("PING");
                    System.out.println("[Client-" + id + "] ← " + in.readLine());
                } catch (Exception e) {
                    System.out.println("[Client-" + id + "] error: " + e.getMessage());
                }
            }, "Client-" + i).start();
        }

        // IO event loop — single IO thread handles all events
        while (running) {
            selector.select(100);
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                if (!key.isValid()) continue;
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, workerPool);
                }
            }
        }

        workerPool.shutdown();
        workerPool.awaitTermination(3, TimeUnit.SECONDS);
        serverChannel.close();
        selector.close();
        System.out.println("[Server] Done.");
    }

    private static void handleAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        if (client == null) return;
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(256));
        System.out.println("[IO Thread] accepted connection from " + client.getRemoteAddress());
    }

    private static void handleRead(SelectionKey key, ExecutorService pool) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buf = (ByteBuffer) key.attachment();
        String message = readMessage(channel, buf);
        if (message == null) { key.cancel(); channel.close(); return; }
        if (message.isEmpty()) return;

        System.out.println("[IO Thread] dispatching to worker pool: " + message);
        key.interestOps(0); // pause reads while worker owns the channel

        pool.submit(() -> { // hand off to worker — IO thread is free immediately
            processRequest(channel, message);
            key.interestOps(SelectionKey.OP_READ);
            key.selector().wakeup();
        });
    }

    /** Reads one message from the channel. Returns null on EOF/error, empty string on no data. */
    private static String readMessage(SocketChannel channel, ByteBuffer buf) throws IOException {
        buf.clear();
        int n = channel.read(buf);
        if (n == -1) return null;
        if (n == 0) return "";
        buf.flip();
        byte[] data = new byte[buf.limit()];
        buf.get(data);
        return new String(data, StandardCharsets.UTF_8).trim();
    }

    private static void processRequest(SocketChannel channel, String message) {
        String thread = Thread.currentThread().getName();
        System.out.println("[" + thread + "] processing: " + message);
        try {
            channel.write(ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            // Client disconnected
        }
    }
}
