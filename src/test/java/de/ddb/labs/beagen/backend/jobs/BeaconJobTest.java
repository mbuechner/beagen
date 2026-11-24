/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package de.ddb.labs.beagen.backend.jobs;

import de.ddb.labs.beagen.backend.data.SECTOR;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks Solr cursor handling with scripted responses and no live DDB dependency. */
class BeaconJobTest {

    private static final JsonMapper JSON = JsonMapper.builderWithJackson2Defaults().build();
    private static final String SEARCH = "http://localhost/search?rows=1";

    @Test
    void downloadsCompletePagesAndKeepsTheSectorCounts() throws IOException {
        final var requestedUrls = new ArrayList<String>();
        final var pages = new ArrayDeque<>(List.of(
                """
                {"response":{"numFound":2,"docs":[{"id":"first","count":5,"count_sec_02":4}]},
                 "nextCursorMark":"a+/="}
                """,
                """
                {"response":{"numFound":2,"docs":[{"id":"second","variant_id":["https://d-nb.info/gnd/123456789"]}]},
                 "nextCursorMark":"b"}
                """));

        final var rows = BeaconJob.readPages(SEARCH, JSON, url -> {
            requestedUrls.add(url);
            return body(pages.removeFirst());
        });

        assertEquals(List.of(SEARCH + "&cursorMark=*", SEARCH + "&cursorMark=a%2B%2F%3D"), requestedUrls);
        assertEquals(List.of("first", "second"), rows.stream().map(BeaconJob.EntityCounts::id).toList());
        assertEquals(5, rows.getFirst().getCount(SECTOR.ALL));
        assertEquals(4, rows.getFirst().getCount(SECTOR.LIBRARY));
        assertEquals(0, rows.getFirst().getCount(SECTOR.ARCHIVE));
        assertEquals(List.of("https://d-nb.info/gnd/123456789"), rows.getLast().variantIds());
    }

    @Test
    void rejectsIncompleteResultsWhenTheCursorStops() {
        final String page = """
                {"response":{"numFound":2,"docs":[{"id":"first"}]},"nextCursorMark":"*"}
                """;

        final var error = assertThrows(IOException.class, () -> BeaconJob.readPages(SEARCH, JSON, url -> body(page)));
        assertTrue(error.getMessage().contains("received 1 of 2"));
    }

    @Test
    void rejectsPartialResultsEvenWhenTheReportedCountMatches() {
        final String page = """
                {"responseHeader":{"partialResults":true},
                 "response":{"numFound":1,"docs":[{"id":"first"}]},"nextCursorMark":"next"}
                """;

        final var error = assertThrows(IOException.class, () -> BeaconJob.readPages(SEARCH, JSON, url -> body(page)));
        assertTrue(error.getMessage().contains("partial"));
    }

    @Test
    void rejectsResultCountChangesDuringTheImport() {
        final var pages = new ArrayDeque<>(List.of(
                """
                {"response":{"numFound":3,"docs":[{"id":"first"}]},"nextCursorMark":"next"}
                """,
                """
                {"response":{"numFound":2,"docs":[{"id":"second"}]},"nextCursorMark":"last"}
                """));

        final var error = assertThrows(IOException.class,
                () -> BeaconJob.readPages(SEARCH, JSON, url -> body(pages.removeFirst())));
        assertTrue(error.getMessage().contains("result count changed"));
    }

    @Test
    void rejectsRepeatedCursorAndRepeatedEntityIds() {
        final var repeatingCursor = new ArrayDeque<>(List.of(
                """
                {"response":{"numFound":3,"docs":[{"id":"first"}]},"nextCursorMark":"next"}
                """,
                """
                {"response":{"numFound":3,"docs":[{"id":"second"}]},"nextCursorMark":"*"}
                """));
        final var cursorError = assertThrows(IOException.class,
                () -> BeaconJob.readPages(SEARCH, JSON, url -> body(repeatingCursor.removeFirst())));
        assertTrue(cursorError.getMessage().contains("previously visited"));

        final String duplicate = """
                {"response":{"numFound":2,"docs":[{"id":"first"},{"id":"first"}]},"nextCursorMark":"next"}
                """;
        final var idError = assertThrows(IOException.class, () -> BeaconJob.readPages(SEARCH, JSON, url -> body(duplicate)));
        assertTrue(idError.getMessage().contains("repeated entity ID"));
    }

    private static ByteArrayInputStream body(String page) {
        return new ByteArrayInputStream(page.getBytes(StandardCharsets.UTF_8));
    }
}
