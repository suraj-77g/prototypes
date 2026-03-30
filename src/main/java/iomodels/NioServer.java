package iomodels;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.*;

/**
 * Demo 2: NIO Selector + worker pool.
 * A single IO thread demultiplexes all events; CPU work is handed off to a pool.
 * Models Tomcat NIO / Jetty — IO thread is never blocked by slow processing.
 */
class NioServer implements IoServer {

    private volatile boolean running;
    private Selector selector;
    private ExecutorService workerPool;
    private Thread ioThread;

    @Override public String name() { return "Demo 2 — NIO Selector + worker pool"; }

    @Override
    public int start() throws Exception {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(0));
        serverChannel.configureBlocking(false);
        int port = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();

        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        workerPool = Executors.newFixedThreadPool(4);
        running = true;

        ioThread = new Thread(this::eventLoop, "IO-Thread");
        ioThread.start();
        return port;
    }

    private void eventLoop() { // single IO thread demultiplexes all channels
        while (running) {
            try {
                selector.select(100);
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next(); keys.remove();
                    if (!key.isValid()) continue;
                    if (key.isAcceptable()) accept(key);
                    else if (key.isReadable()) read(key);
                }
            } catch (IOException e) { break; }
        }
        workerPool.shutdown();
        try { workerPool.awaitTermination(3, TimeUnit.SECONDS); selector.close(); } catch (Exception ignored) {}
    }

    @Override
    public void stop() throws Exception {
        running = false;
        selector.wakeup();
        ioThread.join(4_000);
    }

    private void accept(SelectionKey key) throws IOException {
        SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
        if (client == null) return;
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(256));
        System.out.println("[IO-Thread] accepted: " + client.getRemoteAddress());
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        String msg = readMessage(channel, (ByteBuffer) key.attachment());
        if (msg == null) { key.cancel(); channel.close(); return; }
        if (msg.isEmpty()) return;

        System.out.println("[IO-Thread] dispatching: " + msg);
        key.interestOps(0); // pause reads while worker owns the channel
        workerPool.submit(() -> { // hand off to worker — IO thread is free immediately
            System.out.println("[" + Thread.currentThread().getName() + "] processing: " + msg);
            try { channel.write(ByteBuffer.wrap((msg + "\n").getBytes(StandardCharsets.UTF_8))); } catch (IOException ignored) {}
            key.interestOps(SelectionKey.OP_READ);
            selector.wakeup();
        });
    }

    static String readMessage(SocketChannel ch, ByteBuffer buf) throws IOException {
        buf.clear();
        int n = ch.read(buf);
        if (n == -1) return null;
        if (n == 0) return "";
        buf.flip();
        byte[] data = new byte[buf.limit()]; buf.get(data);
        return new String(data, StandardCharsets.UTF_8).trim();
    }
}
