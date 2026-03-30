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
 * Client #2 sends "SLOW_PING". Watch timestamps — other clients' responses
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
        System.out.println("[Server] Client-2 will send SLOW_PING — watch all others stall!");

        Selector selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Shutdown after 8 s (longer to let starvation fully play out)
        Thread shutdown = new Thread(() -> {
            try {
                Thread.sleep(8_000);
                System.out.println("[Server] Shutting down...");
                running = false;
                selector.wakeup();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        shutdown.setDaemon(true);
        shutdown.start();

        // 5 staggered clients; client #2 sends SLOW_PING
        for (int i = 0; i < 5; i++) {
            final int clientId = i;
            Thread.sleep(200);
            new Thread(() -> runClient(port, clientId), "Client-" + clientId).start();
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
                    handleReadAndProcess(key); // blocking work done inline!
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
        SelectionKey readKey = client.register(selector, SelectionKey.OP_READ);
        readKey.attach(ByteBuffer.allocate(256));
        System.out.println("[EventLoop] accepted connection from " + client.getRemoteAddress());
    }

    private static void handleReadAndProcess(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buf = (ByteBuffer) key.attachment();
        buf.clear();

        int bytesRead;
        try {
            bytesRead = channel.read(buf);
        } catch (IOException e) {
            key.cancel();
            channel.close();
            return;
        }

        if (bytesRead == -1) {
            key.cancel();
            channel.close();
            return;
        }

        if (bytesRead == 0) return;

        buf.flip();
        byte[] data = new byte[buf.limit()];
        buf.get(data);
        String message = new String(data, StandardCharsets.UTF_8).trim();

        if (message.contains("SLOW")) {
            System.out.println("[EventLoop] SLOW request received at " + Instant.now()
                    + " — sleeping 1000ms — ALL connections blocked!");
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("[EventLoop] slow processing done at " + Instant.now());
        } else {
            System.out.println("[EventLoop] processing: " + message + " at " + Instant.now());
        }

        // Echo back inline
        try {
            byte[] response = (message + "\n").getBytes(StandardCharsets.UTF_8);
            channel.write(ByteBuffer.wrap(response));
        } catch (IOException e) {
            // Client disconnected
        }
    }

    private static void runClient(int port, int id) {
        boolean isSlow = (id == 2);
        try (Socket socket = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            for (int i = 0; i < 3; i++) {
                String msg = isSlow ? "SLOW_PING" : "PING";
                System.out.println("[Client-" + id + "] sending " + msg + " at " + Instant.now());
                out.println(msg);
                String reply = in.readLine();
                System.out.println("[Client-" + id + "] received: " + reply + " at " + Instant.now());
                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.out.println("[Client-" + id + "] error: " + e.getMessage());
        }
    }
}
