package iomodels;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * Demo 1: Blocking I/O — ServerSocket + fixed thread pool.
 * Each accepted connection is handed to a pool thread that blocks on readLine().
 */
class BlockingServer implements IoServer {

    private ServerSocket serverSocket;
    private ExecutorService pool;

    @Override public String name() { return "Demo 1 — Blocking I/O (one thread per connection)"; }

    @Override
    public int start() throws Exception {
        serverSocket = new ServerSocket(0);
        serverSocket.setSoTimeout(100); // allows stop() to interrupt accept cleanly
        pool = Executors.newFixedThreadPool(10);
        new Thread(this::acceptLoop, "Blocking-Acceptor").start();
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket conn = serverSocket.accept();
                pool.submit(() -> handle(conn)); // one thread per connection
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) { break; }
        }
    }

    @Override
    public void stop() throws Exception {
        serverSocket.close();
        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);
    }

    private static void handle(Socket socket) {
        String thread = Thread.currentThread().getName();
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String line;
            while ((line = in.readLine()) != null) { // blocks this thread until data arrives
                System.out.println("[" + thread + "] echoed: " + line);
                out.println(line);
            }
        } catch (IOException ignored) {}
    }
}
