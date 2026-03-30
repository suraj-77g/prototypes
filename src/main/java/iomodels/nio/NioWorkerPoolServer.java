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

        // Shutdown after 5 s
        Thread shutdown = new Thread(() -> {
            try {
                Thread.sleep(5_000);
                System.out.println("[Server] Shutting down...");
                running = false;
                selector.wakeup();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        shutdown.setDaemon(true);
        shutdown.start();

        // 5 staggered clients
        for (int i = 0; i < 5; i++) {
            final int clientId = i;
            Thread.sleep(200);
            new Thread(() -> runClient(port, clientId), "Client-" + clientId).start();
        }

        // IO event loop — runs on main thread
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
        SelectionKey readKey = client.register(selector, SelectionKey.OP_READ);
        readKey.attach(ByteBuffer.allocate(256));
        System.out.println("[IO Thread] accepted connection from " + client.getRemoteAddress());
    }

    private static void handleRead(SelectionKey key, ExecutorService pool) throws IOException {
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

        System.out.println("[IO Thread] dispatching to worker pool: " + message);

        // Deregister read interest while worker processes to avoid re-entry
        key.interestOps(0);

        pool.submit(() -> {
            processRequest(channel, message);
            // Re-enable read interest after processing
            key.interestOps(SelectionKey.OP_READ);
            key.selector().wakeup();
        });
    }

    private static void processRequest(SocketChannel channel, String message) {
        String thread = Thread.currentThread().getName();
        System.out.println("[" + thread + "] processing: " + message);
        try {
            byte[] response = (message + "\n").getBytes(StandardCharsets.UTF_8);
            channel.write(ByteBuffer.wrap(response));
        } catch (IOException e) {
            // Client disconnected
        }
    }

    private static void runClient(int port, int id) {
        try (Socket socket = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            for (int i = 0; i < 3; i++) {
                String msg = "PING";
                System.out.println("[Client-" + id + "] sending " + msg);
                out.println(msg);
                String reply = in.readLine();
                System.out.println("[Client-" + id + "] received: " + reply);
                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.out.println("[Client-" + id + "] error: " + e.getMessage());
        }
    }
}
