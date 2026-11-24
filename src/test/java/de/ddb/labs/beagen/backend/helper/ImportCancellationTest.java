/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package de.ddb.labs.beagen.backend.helper;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises cancellation while a successful HTTP response is still streaming. */
class ImportCancellationTest {

    @Test
    void cancelsAnImportThatIsWaitingForTheRestOfTheBody() throws Exception {
        final var cancellation = new ImportCancellation();
        final var firstByteRead = new CountDownLatch(1);
        final var finishResponse = new CountDownLatch(1);
        final var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 0);
                final var output = exchange.getResponseBody();
                output.write('a');
                output.flush();
                finishResponse.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        final String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";

        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                final var download = worker.submit(() -> {
                    try (var input = DDBApi.httpGet(url, cancellation)) {
                        assertEquals('a', input.read());
                        firstByteRead.countDown();
                        return input.read();
                    }
                });
                // Wait until headers and body data have arrived, while the server keeps the stream open.
                assertTrue(firstByteRead.await(5, TimeUnit.SECONDS));
                cancellation.cancel();

                final var failure = assertThrows(ExecutionException.class, () -> download.get(5, TimeUnit.SECONDS));
                assertInstanceOf(IOException.class, failure.getCause());
                assertThrows(InterruptedIOException.class, () -> DDBApi.httpGet(url, cancellation));
            } finally {
                cancellation.cancel();
                finishResponse.countDown();
                server.stop(0);
            }
        }
    }
}
