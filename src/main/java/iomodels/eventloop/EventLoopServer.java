package iomodels.eventloop;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;

/**
 * Demo 3: Single-thread event loop — NO worker pool.
 * All accept, read, and processing happens inline on the event loop thread.
 * Models Node.js / libuv architecture including its key failure mode:
 * a slow handler (SLOW_PING) blocks ALL other connections for 1 second.
 *
 * Client-1 sends "SLOW_PING". Watch timestamps — other clients' responses
 * are delayed by the full 1 s sleep even though they sent PING before it finished.
 *
 * Run: mvn exec:java -Dexec.mainClass="iomodels.eventloop.EventLoopServer"
 */
public class EventLoopServer {

    private static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(0));
        serverChannel.configureBlocking(false);
        int port = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
        System.out.println("[Server] EventLoopServer listening on port " + port);
        System.out.println("[Server] Client-1 will send SLOW_PING — watch all others stall!");

        Selector selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Shutdown after 8 s — needs selector.wakeup() to unblock select()
        Thread shutdown = new Thread(() -> {
            try { Thread.sleep(8_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.println("[Server] Shutting down...");
            running = false;
            selector.wakeup();
        });
        shutdown.setDaemon(true);
        shutdown.start();

        // 3 staggered clients; client-1 sends SLOW_PING
        for (int i = 0; i < 3; i++) {
            final int id = i;
            Thread.sleep(200);
            new Thread(() -> {
                boolean isSlow = (id == 1);
                String msg = isSlow ? "SLOW_PING" : "PING";
                try (Socket s = new Socket("localhost", port);
                     PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    System.out.println("[Client-" + id + "] → " + msg + " at " + Instant.now());
                    out.println(msg);
                    System.out.println("[Client-" + id + "] ← " + in.readLine() + " at " + Instant.now());
                } catch (Exception e) {
                    System.out.println("[Client-" + id + "] error: " + e.getMessage());
                }
            }, "Client-" + i).start();
        }

        // Single-thread event loop — everything inline, no pool
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
                    handleReadAndProcess(key); // all work inline — no hand-off
                }
            }
        }

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
        System.out.println("[EventLoop] accepted connection from " + client.getRemoteAddress());
    }

    private static void handleReadAndProcess(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buf = (ByteBuffer) key.attachment();
        String message = readMessage(channel, buf);
        if (message == null) { key.cancel(); channel.close(); return; }
        if (message.isEmpty()) return;

        if (message.contains("SLOW")) {
            System.out.println("[EventLoop] SLOW request at " + Instant.now() + " — sleeping 1000ms — ALL connections blocked!"); // blocks the ONLY thread — zero events processed for 1s
            try { Thread.sleep(1_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.println("[EventLoop] slow processing done at " + Instant.now());
        } else {
            System.out.println("[EventLoop] processing: " + message + " at " + Instant.now());
        }

        try {
            channel.write(ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            // Client disconnected
        }
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
}
