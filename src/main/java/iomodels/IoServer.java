package iomodels;

/**
 * Contract for all I/O model demos.
 * Implementations differ only in their threading strategy.
 */
interface IoServer {
    String name();
    int start() throws Exception;  // binds, starts accepting; returns port
    void stop() throws Exception;  // signals shutdown, waits for drain
}
