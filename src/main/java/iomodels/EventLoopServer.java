package iomodels;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;

/**
 * Demo 3: Single-thread event loop — NO worker pool.
 * All processing is inline on the loop thread. A slow handler (SLOW_PING)
 * blocks ALL connections for 1 second — the starvation problem Node.js faces
 * when CPU-bound work leaks into the event loop.
 *
 * Watch timestamps: client-1's 1s sleep delays every other client's response.
 */
class EventLoopServer implements IoServer {

    private volatile boolean running;
    private Selector selector;
    private Thread loopThread;

    @Override public String name() { return "Demo 3 — Single-thread event loop (starvation demo)"; }

    @Override
    public int start() throws Exception {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(0));
        serverChannel.configureBlocking(false);
        int port = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();

        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        running = true;

        loopThread = new Thread(this::eventLoop, "EventLoop");
        loopThread.start();
        return port;
    }

    private void eventLoop() { // single thread — all work inline, no hand-off
        while (running) {
            try {
                selector.select(100);
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next(); keys.remove();
                    if (!key.isValid()) continue;
                    if (key.isAcceptable()) accept(key);
                    else if (key.isReadable()) process(key); // all work inline — no hand-off
                }
            } catch (IOException e) { break; }
        }
        try { selector.close(); } catch (IOException ignored) {}
    }

    @Override
    public void stop() throws Exception {
        running = false;
        selector.wakeup();
        loopThread.join(10_000);
    }

    private void accept(SelectionKey key) throws IOException {
        SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
        if (client == null) return;
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(256));
        System.out.println("[EventLoop] accepted: " + client.getRemoteAddress());
    }

    private void process(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        String msg = NioServer.readMessage(channel, (ByteBuffer) key.attachment());
        if (msg == null) { key.cancel(); channel.close(); return; }
        if (msg.isEmpty()) return;

        if (msg.contains("SLOW")) {
            System.out.println("[EventLoop] SLOW at " + Instant.now() + " — sleeping 1s — ALL connections blocked!"); // blocks the ONLY thread
            try { Thread.sleep(1_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.println("[EventLoop] SLOW done at " + Instant.now());
        } else {
            System.out.println("[EventLoop] processed: " + msg + " at " + Instant.now());
        }

        try { channel.write(ByteBuffer.wrap((msg + "\n").getBytes(StandardCharsets.UTF_8))); } catch (IOException ignored) {}
    }
}
