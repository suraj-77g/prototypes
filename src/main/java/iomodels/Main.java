package iomodels;

/**
 * Runs all three I/O model demos sequentially.
 *
 * Demo 1 — Blocking:    pool thread names show one-thread-per-connection mapping.
 * Demo 2 — NIO+Pool:    [IO-Thread] dispatches, [pool-N-thread-M] processes.
 * Demo 3 — Event Loop:  SLOW_PING from client-1 visibly delays all other clients.
 *
 * Run: mvn exec:java -Dexec.mainClass="iomodels.Main"
 */
public class Main {

    public static void main(String[] args) throws Exception {
        demo(new BlockingServer(),   5_000, false);
        demo(new NioServer(),        5_000, false);
        demo(new EventLoopServer(),  8_000, true);
    }

    private static void demo(IoServer server, int durationMs, boolean includeSlow) throws Exception {
        System.out.println("\n=== " + server.name() + " ===");
        int port = server.start();
        for (int i = 0; i < 3; i++) {
            Thread.sleep(200);
            Client.spawn(port, i, (includeSlow && i == 1) ? "SLOW_PING" : "PING");
        }
        Thread.sleep(durationMs);
        server.stop();
        System.out.println("=== done ===");
    }
}
