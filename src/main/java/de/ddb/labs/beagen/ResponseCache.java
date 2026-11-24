/*
 * Copyright 2019-2026 Michael Büchner, Deutsche Digitale Bibliothek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ddb.labs.beagen;

import de.ddb.labs.beagen.backend.BeaconFileController;
import de.ddb.labs.beagen.backend.data.SECTOR;
import de.ddb.labs.beagen.backend.data.TYPE;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.HttpStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * Serves the static HTML shell and caches serialized JSON metadata by list selection.
 * Lists expire after a short interval or when Beagen saves or deletes entries. Large
 * responses are served normally and evicted to limit retained memory.
 */
final class ResponseCache {

    private static final long LIST_TTL_NANOS = Duration.ofMinutes(1).toNanos();
    private static final int MAX_CACHED_LIST_BYTES = 1024 * 1024;
    private static final Pattern ENTITY_TAG = Pattern.compile("(?:W/)?\"[^\"]*\"");

    // Only validated type/sector combinations and the two list routes get entries.
    private final ConcurrentMap<ListKey, ListEntry> lists = new ConcurrentHashMap<>();
    private final Page html;

    ResponseCache() throws IOException {
        try (InputStream input = ResponseCache.class.getResourceAsStream("/base.html")) {
            if (input == null) {
                throw new IOException("Missing list page /base.html");
            }
            html = new Page(input.readAllBytes());
        }
    }

    void serveHtml(Context ctx) {
        html.send(ctx, ContentType.HTML);
    }

    void serveList(Context ctx, TYPE type, SECTOR sector, boolean latest) {
        final ListKey key = new ListKey(type, sector, latest);
        final ListEntry entry = lists.compute(key, (ignored, cached) -> {
            final long revision = BeaconFileController.getListRevision();
            if (cached != null && cached.revision() == revision
                    && System.nanoTime() - cached.loadedAt() < LIST_TTL_NANOS) {
                return cached;
            }
            final var summaries = BeaconFileController.getBeaconFileSummaries(type, sector, latest);
            final String json = ctx.jsonMapper().toJsonString(summaries, List.class);
            final Page page = new Page(json.getBytes(StandardCharsets.UTF_8));
            return new ListEntry(revision, System.nanoTime(), page);
        });
        final Page page = entry.page();
        if (page.body().length > MAX_CACHED_LIST_BYTES) {
            lists.remove(key, entry);
        }
        page.send(ctx, ContentType.JSON);
    }

    private record ListKey(TYPE type, SECTOR sector, boolean latest) {
    }

    private record ListEntry(long revision, long loadedAt, Page page) {
    }

    private record Page(byte[] body, String etag) {

        private Page(byte[] body) {
            this(body, weakEtag(body));
        }

        private void send(Context ctx, String contentType) {
            // Clients may store the body but must revalidate it on each request.
            ctx.header(Header.CACHE_CONTROL, "no-cache");
            ctx.header(Header.ETAG, etag);
            final var ifNoneMatch = ctx.req().getHeaders(Header.IF_NONE_MATCH);
            if (ifNoneMatch.hasMoreElements() && matchesEtag(String.join(",", Collections.list(ifNoneMatch)))) {
                ctx.status(HttpStatus.NOT_MODIFIED);
                return;
            }
            ctx.contentType(contentType).result(body);
        }

        private boolean matchesEtag(String ifNoneMatch) {
            if (ifNoneMatch.strip().equals("*")) {
                return true;
            }
            final var tags = ENTITY_TAG.matcher(ifNoneMatch);
            while (tags.find()) {
                final String tag = tags.group();
                if (tag.equals(etag) || tag.equals(etag.substring(2))) {
                    return true;
                }
            }
            return false;
        }

        private static String weakEtag(byte[] body) {
            try {
                // A weak validator identifies the same content across compression variants.
                final byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
                return "W/\"" + HexFormat.of().formatHex(digest) + "\"";
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Missing SHA-256", e);
            }
        }
    }
}
