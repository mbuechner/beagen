/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package de.ddb.labs.beagen;

import de.ddb.labs.beagen.backend.helper.Configuration;
import io.javalin.Javalin;
import java.io.ByteArrayOutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.impl.SchedulerRepository;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises startup, migration, and HTTP routes against a fresh in-memory database. */
class MainTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String HISTORY = "/catalog/list?type=organisation&sector=all";
    private static final String LATEST = "/catalog/list/latest?type=organisation&sector=all";

    private final Map<String, String> originalSettings = new LinkedHashMap<>();
    private String originalDatabaseProperty;
    private String database;

    @BeforeEach
    void configureIsolatedApplication() throws Exception {
        originalDatabaseProperty = System.getProperty("beagen.database");
        database = "mem:beagen_test_" + UUID.randomUUID().toString().replace("-", "");
        set("beagen.database", database);
        set("beagen.port", "0");
        set("beagen.pathprefix", "/catalog");
        set("beagen.baseurl", "http://localhost/catalog");
        // Keep the scheduler active without contacting the live DDB API.
        set("beagen.cron", "0 0 0 1 1 ? 2099");
    }

    @AfterEach
    void restoreConfiguration() throws Exception {
        for (var setting : originalSettings.entrySet()) {
            Configuration.setValue(setting.getKey(), setting.getValue());
        }
        if (originalDatabaseProperty == null) {
            System.clearProperty("beagen.database");
        } else {
            System.setProperty("beagen.database", originalDatabaseProperty);
        }
    }

    @Test
    void migratedHistoryAndDownloadsRemainAvailableThroughTheServer() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:" + database);
                var statement = connection.createStatement()) {
            // Reproduce the fixed-width enum columns that pad values in historical databases.
            statement.execute("CREATE SEQUENCE HIBERNATE_SEQUENCE START WITH 10");
            statement.execute("""
                    CREATE TABLE BEACONFILE (ID BIGINT PRIMARY KEY, TYPE CHAR(255) NOT NULL,
                        SECTOR CHAR(255) NOT NULL, CREATED TIMESTAMP NOT NULL, COUNT INTEGER NOT NULL,
                        CONTENT BLOB NOT NULL)
                    """);
            // Rows 1 and 2 share a timestamp so the latest route must break the tie by ID.
            try (var insert = connection.prepareStatement("""
                    INSERT INTO BEACONFILE VALUES (?, ?, 'ALL', TIMESTAMP '2025-11-24 12:00:00.123', 1, ?)
                    """)) {
                insert.setString(2, "ORGANISATION");
                insert.setLong(1, 1);
                insert.setBytes(3, gzip("100000001\n"));
                insert.executeUpdate();
                insert.setLong(1, 2);
                insert.setBytes(3, gzip("100000002\n"));
                insert.executeUpdate();
                insert.setLong(1, 3);
                insert.setString(2, "PERSON");
                insert.setBytes(3, new byte[]{1, 2, 3});
                insert.executeUpdate();
            }

            // Turkish casing exposes accidental locale dependence in enum names and feed URLs.
            final Locale originalLocale = Locale.getDefault();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                final Javalin app = Main.start();
                try {
                    final var redirect = get(client, app, "/catalog", "text/html");
                    assertEquals(301, redirect.statusCode());
                    assertEquals(LATEST, redirect.headers().firstValue("location").orElseThrow());

                    final var html = get(client, app, HISTORY, "application/json;q=0.3, text/html;q=0.9");
                    assertEquals(200, html.statusCode());
                    assertTrue(html.body().contains("id=\"beaconTable\""));
                    assertTrue(get(client, app, HISTORY, "text/*;q=1, application/json;q=0.5")
                            .headers().firstValue("content-type").orElseThrow().startsWith("text/html"));
                    assertEquals(406, get(client, app, HISTORY, "application/json;q=0, text/html;q=0").statusCode());

                    final var history = get(client, app, HISTORY, "text/html;q=0, application/json;q=0.8");
                    assertEquals(200, history.statusCode());
                    final var records = JSON.readTree(history.body());
                    assertEquals(2, records.size());
                    assertTrue(records.get(0).get("@id").asString().endsWith("/item/2"));
                    assertTrue(records.get(1).get("@id").asString().endsWith("/item/1"));
                    final var latest = JSON.readTree(get(client, app, LATEST, "application/json").body());
                    assertEquals(1, latest.size());
                    assertEquals(records.get(0), latest.get(0));

                    final String etag = history.headers().firstValue("etag").orElseThrow();
                    final var notModified = client.send(HttpRequest.newBuilder(uri(app, HISTORY))
                            .header("Accept", "application/json").header("If-None-Match", etag).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertEquals(304, notModified.statusCode());
                    assertTrue(notModified.body().isEmpty());

                    final var download = get(client, app, "/catalog/item/organisation/all/latest", "text/plain");
                    assertEquals(200, download.statusCode());
                    assertTrue(download.body().contains("/item/organisation/all/latest"));
                    assertTrue(download.body().endsWith("100000002\n"));
                    assertEquals(400, get(client, app, "/catalog/item/invalid", "text/plain").statusCode());
                    assertEquals(404, get(client, app, "/catalog/item/999", "text/plain").statusCode());
                    assertEquals(500, get(client, app, "/catalog/item/3", "text/plain").statusCode());
                } finally {
                    app.stop();
                }
            } finally {
                Locale.setDefault(originalLocale);
            }
            assertTrue(SchedulerRepository.getInstance().lookupAll().isEmpty());
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM BEACONFILE WHERE TYPE <> RTRIM(TYPE) OR SECTOR <> RTRIM(SECTOR)")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM BEACONFILE")) {
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
            }
        }
    }

    @Test
    void failedServerStartupReleasesQuartz() throws Exception {
        try (var listener = new ServerSocket(0)) {
            set("beagen.port", Integer.toString(listener.getLocalPort()));
            Throwable failure = assertThrows(Exception.class, Main::start);
            while (failure != null && !(failure instanceof BindException)) {
                failure = failure.getCause();
            }
            assertTrue(failure instanceof BindException, "The HTTP port must be the reason startup failed");
        }
        assertTrue(SchedulerRepository.getInstance().lookupAll().isEmpty());
    }

    private void set(String key, String value) throws Exception {
        originalSettings.putIfAbsent(key, Configuration.getValue(key));
        Configuration.setValue(key, value);
    }

    private static byte[] gzip(String content) throws Exception {
        final var bytes = new ByteArrayOutputStream();
        try (var stream = new GZIPOutputStream(bytes)) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static HttpResponse<String> get(HttpClient client, Javalin app, String path, String accept) throws Exception {
        final var request = HttpRequest.newBuilder(uri(app, path)).header("Accept", accept)
                .timeout(Duration.ofSeconds(10)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(Javalin app, String path) {
        return URI.create("http://127.0.0.1:" + app.port() + path);
    }
}
