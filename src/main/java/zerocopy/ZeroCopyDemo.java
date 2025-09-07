package zerocopy;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;

public class ZeroCopyDemo {

    private static final String FILE_NAME = "large_test_file.txt";
    private static final long FILE_SIZE = 200 * 1024 * 1024; // 200 MiB

    public static void main(String[] args) throws IOException {
        // Step 1: Create a large file to test with
        createLargeFile();

        // Step 2: Perform a traditional copy and measure performance
        System.out.println("--- Starting Traditional Copy (with user-space buffer) ---");
        Instant startTraditional = Instant.now();
        traditionalCopy("traditional_copy_dest.txt");
        Instant endTraditional = Instant.now();
        long traditionalTime = Duration.between(startTraditional, endTraditional).toMillis();
        System.out.printf("Traditional Copy took: %d ms%n%n", traditionalTime);

        // Step 3: Perform a zero-copy and measure performance
        System.out.println("--- Starting Zero-Copy (using FileChannel.transferTo) ---");
        Instant startZeroCopy = Instant.now();
        zeroCopy("zero_copy_dest.txt");
        Instant endZeroCopy = Instant.now();
        long zeroCopyTime = Duration.between(startZeroCopy, endZeroCopy).toMillis();
        System.out.printf("Zero-Copy took: %d ms%n%n", zeroCopyTime);

        // Step 4: Display results and clean up
        System.out.println("--- Comparison ---");
        System.out.printf("Zero-Copy was %.2f times faster than Traditional Copy.%n",
                (double) traditionalTime / zeroCopyTime);

        cleanup();
    }

    /**
     * Creates a 200MiB text file with sample content.
     */
    private static void createLargeFile() throws IOException {
        System.out.println("Creating a 200 MiB test file...");
        Path path = Paths.get(FILE_NAME);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            String line = "This is a sample line for our large file to demonstrate zero-copy.\n";
            long writtenBytes = 0;
            while (writtenBytes < FILE_SIZE) {
                writer.write(line);
                writtenBytes += line.getBytes().length;
            }
        }
        System.out.println("Test file created successfully.");
    }

    /**
     * Copies the file using a traditional approach with a user-space buffer.
     * This involves copying data from kernel space to user space (Java app) and back.
     */
    private static void traditionalCopy(String destFileName) throws IOException {
        try (InputStream in = new FileInputStream(FILE_NAME);
             OutputStream out = new FileOutputStream(destFileName)) {
            byte[] buffer = new byte[8192]; // 8KB buffer
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Copies the file using the zero-copy approach via FileChannel.transferTo().
     * This allows the OS to transfer bytes directly from the source file channel
     * to the destination channel in kernel space.
     */
    private static void zeroCopy(String destFileName) throws IOException {
        try (FileChannel source = new FileInputStream(FILE_NAME).getChannel();
             FileChannel destination = new FileOutputStream(destFileName).getChannel()) {
            source.transferTo(0, source.size(), destination);
        }
    }

    /**
     * Deletes the generated files.
     */
    private static void cleanup() throws IOException {
        Files.deleteIfExists(Paths.get(FILE_NAME));
        Files.deleteIfExists(Paths.get("traditional_copy_dest.txt"));
        Files.deleteIfExists(Paths.get("zero_copy_dest.txt"));
        System.out.println("Cleaned up test files.");
    }

}