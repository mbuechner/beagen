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
package de.ddb.labs.beagen.backend.helper;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Looks up current Integrated Authority File (GND) identifiers through Entity Facts.
 *
 * @author Michael Büchner
 */
public class EntityFacts {

    private static final Logger log = LoggerFactory.getLogger(EntityFacts.class);
    private static final HttpUrl ENTITY_FACTS_URL = HttpUrl.get("https://hub.culturegraph.org/entityfacts/");
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build();

    private EntityFacts() {
    }
    
    /**
     * Resolves a GND identifier from the final Entity Facts URL after redirects.
     * A successful response must identify a record on the Entity Facts host.
     *
     * @param id the GND identifier without the URI prefix
     * @param cancellation the cancellation tracker for the import
     * @return the resolved identifier, or the original identifier if the lookup fails
     * @throws InterruptedIOException if the import is cancelled
     * @throws IllegalStateException if the tracker already has an active request
     */
    public static String getGndId(String id, ImportCancellation cancellation) throws InterruptedIOException {

        final Request request = new Request.Builder()
                .url(ENTITY_FACTS_URL.newBuilder().addPathSegment(id).build())
                .head()
                .build();
        final Call call = client.newCall(request);
        cancellation.begin(call);
        try (Response response = call.execute()) {
            final HttpUrl url = response.request().url();
            final List<String> path = url.pathSegments();
            if (response.isSuccessful() && ENTITY_FACTS_URL.host().equals(url.host())
                    && path.size() == 2 && path.getFirst().equals("entityfacts") && !path.getLast().isBlank()) {
                return path.getLast();
            }
        } catch (IOException ex) {
            cancellation.check();
            log.warn("Could not get data from Entity Facts. {}", ex.getMessage());
        } finally {
            cancellation.finish(call);
        }
        return id;
    }
}
