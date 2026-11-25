# Multithreaded TCP Socket Client

A Java command-line client for measuring how server response times change as more requests arrive together. It opens a separate TCP connection for each request, sends the same operation across all connections, and reports individual and average turnaround times.

## Server comparison

This repository contains the client-side code for testing two server designs. Both clients generate concurrent requests. The folder names identify how the corresponding server handles them.

| Client | Server behavior | Supported batch sizes |
|---|---|---|
| [Iterative server client](./iterative-server/Client.java) | Processes one request at a time | 1, 5, 10, 15, 20, 25 |
| [Concurrent server client](./concurrent-server/Client.java) | Uses a separate worker for each request | 1, 5, 10, 15, 20, 25, 100 |

## Getting started

You’ll need a Java Development Kit (JDK) and a running TCP server that supports the request protocol described below.

1. Open a terminal in the `iterative-server` or `concurrent-server` folder.
2. Compile and launch the client:

   ```bash
   javac Client.java
   java Client
   ```

3. Enter the server address and port. The client accepts ports from `1025` through `4998`.
4. Select an operation and a batch size. For a first run, choose `1` for **Date and Time** and send one request. Repeat with five requests to see how the turnaround times vary within a batch.

## Request protocol

Each worker sends one operation number followed by a newline. The server returns a UTF-8 text response and closes its output stream to signal that the response is complete.

| Operation number | Requested information |
|---|---|
| `1` | Server date and time |
| `2` | Server uptime |
| `3` | Memory usage |
| `4` | Network connections |
| `5` | Currently connected users |
| `6` | Running processes |

## How timing works

The client creates one thread per request and uses a `CountDownLatch` to give the workers a shared start signal. Each worker opens its connection, starts its timer immediately before writing the request, and stops it after reading the complete response. This measures the wait experienced by the client after connection setup, including any time the request spends waiting for the server to handle it.

After each batch, the client prints each response and its turnaround time in milliseconds. The summary includes the number of successful and failed requests, the sum of successful request times, and their average. Because requests overlap, the sum can exceed the elapsed time for the batch. If any requests fail, the client flags the batch as incomplete.
