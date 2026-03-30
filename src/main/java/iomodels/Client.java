package iomodels;

import java.io.*;
import java.net.*;
import java.time.Instant;

/** Spawns a client thread that sends one message and prints the reply with timestamps. */
class Client {
    static void spawn(int port, int id, String message) {
        new Thread(() -> {
            try (Socket s = new Socket("localhost", port);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                System.out.println("[Client-" + id + "] → " + message + " at " + Instant.now());
                out.println(message);
                System.out.println("[Client-" + id + "] ← " + in.readLine() + " at " + Instant.now());
            } catch (Exception e) {
                System.out.println("[Client-" + id + "] error: " + e.getMessage());
            }
        }, "Client-" + id).start();
    }
}
