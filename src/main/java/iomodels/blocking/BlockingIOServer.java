package iomodels.blocking;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * Demo 1: Blocking I/O — ServerSocket + fixed thread pool.
 * One thread per connection. Thread names directly reveal the mapping.
 *
 * Run: mvn exec:java -Dexec.mainClass="iomodels.blocking.BlockingIOServer"
 */
public class BlockingIOServer {

    private static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(0); // OS picks port
        int port = serverSocket.getLocalPort();
        System.out.println("[Server] BlockingIOServer listening on port " + port);

        ExecutorService pool = Executors.newFixedThreadPool(10);

        // Shutdown after 5 s
        Thread shutdown = new Thread(() -> {
            try {
                Thread.sleep(5_000);
                System.out.println("[Server] Shutting down...");
                running = false;
                serverSocket.close();
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        });
        shutdown.setDaemon(true);
        shutdown.start();

        // 5 client threads staggered 200 ms apart
        for (int i = 0; i < 5; i++) {
            final int clientId = i;
            Thread.sleep(200);
            new Thread(() -> runClient(port, clientId), "Client-" + clientId).start();
        }

        // Accept loop
        while (running) {
            try {
                Socket conn = serverSocket.accept();
                pool.submit(() -> handleConnection(conn));
            } catch (SocketException e) {
                break; // serverSocket.close() called by shutdown thread
            }
        }

        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);
        System.out.println("[Server] Done.");
    }

    private static void handleConnection(Socket socket) {
        String thread = Thread.currentThread().getName();
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[" + thread + "] echoed: " + line);
                out.println(line);
            }
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
