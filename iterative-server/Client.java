import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {

    public static void main(String[] args) throws InterruptedException {

        try (Scanner keyboard = new Scanner(System.in)) {

            System.out.print("Enter server IP address: ");
            String serverAddress = keyboard.nextLine().trim();

            int port = readNumber(
                    keyboard,
                    "Enter server port (1025-4998): ",
                    1025,
                    4998
            );

            while (true) {

                System.out.println("\nChoose an operation:");
                System.out.println("1. Date and Time");
                System.out.println("2. Uptime");
                System.out.println("3. Memory Use");
                System.out.println("4. Netstat");
                System.out.println("5. Current Users");
                System.out.println("6. Running Processes");
                System.out.println("0. Quit");

                int operation = readNumber(
                        keyboard, "Your choice: ", 0, 6
                );

                if (operation == 0) {
                    System.out.println("Goodbye!");
                    break;
                }

                int numberOfRequests;

                while (true) {

                    numberOfRequests = readNumber(
                            keyboard,
                            "Number of requests (1, 5, 10, 15, 20, 25): ",
                            1,
                            25
                    );

                    if (numberOfRequests == 1
                            || numberOfRequests % 5 == 0) {
                        break;
                    }

                    System.out.println(
                            "Please enter 1, 5, 10, 15, 20, or 25."
                    );
                }

                System.out.println("\nYour selected settings:");
                System.out.println("Server: " + serverAddress);
                System.out.println("Port: " + port);
                System.out.println("Operation: " + operation);
                System.out.println("Requests: " + numberOfRequests);
                runBatch(serverAddress, port, operation, numberOfRequests);
            }
        }
    }

    // Keep asking until the user enters an integer in the allowed range.
    private static int readNumber(
            Scanner keyboard, String prompt, int minimum, int maximum) {

        while (true) {

            System.out.print(prompt);

            try {
                int number =
                        Integer.parseInt(keyboard.nextLine().trim());

                if (number >= minimum && number <= maximum) {
                    return number;
                }

                System.out.println(
                        "Please enter a number from "
                        + minimum + " to " + maximum + "."
                );

            } catch (NumberFormatException e) {

                System.out.println("Please enter a whole number.");
            }
        }
    }
    
 // Stores the outcome of one request.
    private static class RequestResult {

        String response = "";
        double elapsedMilliseconds;
        String error;

        boolean succeeded() {
            return error == null;
        }
    }

    // Opens one connection, sends one request, and records the result.
    private static RequestResult sendOneRequest(
            String serverAddress, int port, int operation) {

        RequestResult result = new RequestResult();

        try (Socket socket = new Socket()) {

            socket.connect(
                    new InetSocketAddress(serverAddress, port),
                    10000
            );

            socket.setSoTimeout(60000);

            try (
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(
                                socket.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                BufferedWriter output = new BufferedWriter(
                        new OutputStreamWriter(
                                socket.getOutputStream(),
                                StandardCharsets.UTF_8
                        )
                )
            ) {

                StringBuilder response = new StringBuilder();

                // Timing begins after connection and stream setup.
                long startTime = System.nanoTime();

                output.write(Integer.toString(operation));
                output.newLine();
                output.flush();

                String line;

                while ((line = input.readLine()) != null) {
                    response.append(line).append("\n");
                }

                long endTime = System.nanoTime();

                result.elapsedMilliseconds =
                        (endTime - startTime) / 1_000_000.0;

                result.response = response.toString();
            }

        } catch (IOException e) {

            result.error =
                    e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        return result;
    }

    // Starts multiple requests and displays their results.
    private static void runBatch(
            String serverAddress,
            int port,
            int operation,
            int numberOfRequests) throws InterruptedException {

        Thread[] threads = new Thread[numberOfRequests];
        RequestResult[] results = new RequestResult[numberOfRequests];

        // All workers wait here until the main thread releases them.
        CountDownLatch startSignal = new CountDownLatch(1);

        for (int i = 0; i < numberOfRequests; i++) {

            final int index = i;

            threads[i] = new Thread(() -> {

                try {
                    startSignal.await();

                    results[index] = sendOneRequest(
                            serverAddress, port, operation
                    );

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    RequestResult interrupted = new RequestResult();
                    interrupted.error = "Request interrupted before starting.";
                    results[index] = interrupted;
                }

            }, "Client-" + (i + 1));

            threads[i].start();
        }

        System.out.println(
                "\nStarting " + numberOfRequests + " client requests..."
        );

        // Let the workers begin connecting.
        startSignal.countDown();

        // Wait until every worker has finished.
        for (Thread thread : threads) {
            thread.join();
        }

        double totalMilliseconds = 0;
        int successfulRequests = 0;

        for (int i = 0; i < numberOfRequests; i++) {

            RequestResult result = results[i];

            System.out.println("\n--- Client " + (i + 1) + " ---");

            if (result.succeeded()) {

                System.out.println("Server response:");
                System.out.print(result.response);

                System.out.printf(
                        "Turnaround time: %.3f ms%n",
                        result.elapsedMilliseconds
                );

                totalMilliseconds += result.elapsedMilliseconds;
                successfulRequests++;

            } else {

                System.out.println("Request failed: " + result.error);
            }
        }

        System.out.println("\n--- Batch summary ---");
        System.out.println("Requested: " + numberOfRequests);
        System.out.println("Successful: " + successfulRequests);
        System.out.println(
                "Failed: " + (numberOfRequests - successfulRequests)
        );

        if (successfulRequests == numberOfRequests) {

            System.out.printf(
                    "Total turnaround time: %.3f ms%n",
                    totalMilliseconds
            );

            System.out.printf(
                    "Average turnaround time: %.3f ms%n",
                    totalMilliseconds / numberOfRequests
            );

        } else if (successfulRequests > 0) {

            System.out.printf(
                    "Total for successful requests only: %.3f ms%n",
                    totalMilliseconds
            );

            System.out.printf(
                    "Average for successful requests only: %.3f ms%n",
                    totalMilliseconds / successfulRequests
            );

            System.out.println(
                    "Incomplete batch: resolve failures and rerun "
                    + "before using it for the report."
            );

        } else {

            System.out.println(
                    "No successful requests; no average to calculate."
            );
        }
    }
}
