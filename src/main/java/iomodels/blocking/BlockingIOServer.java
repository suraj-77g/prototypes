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

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(0); // OS picks port
        serverSocket.setSoTimeout(5_000);                // accept loop exits after 5 s idle
        int port = serverSocket.getLocalPort();
        System.out.println("[Server] BlockingIOServer listening on port " + port);

        ExecutorService pool = Executors.newFixedThreadPool(10);

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

        // Accept loop — one thread per connection
        while (true) {
            try {
                Socket conn = serverSocket.accept();
                pool.submit(() -> handleConnection(conn)); // one thread per connection
            } catch (SocketTimeoutException e) {
                break; // 5 s with no new connections — we're done
            }
        }

        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);
        serverSocket.close();
        System.out.println("[Server] Done.");
    }

    private static void handleConnection(Socket socket) {
        String thread = Thread.currentThread().getName();
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String line;
            while ((line = in.readLine()) != null) { // blocks this thread until data arrives
                System.out.println("[" + thread + "] echoed: " + line);
                out.println(line);
            }
        } catch (IOException e) {
            // Client disconnected
        }
    }
}
