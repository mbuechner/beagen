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

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Michael Büchner
 */
@Slf4j
public class EntityFacts {

    private static final String EF_URL = "http://hub.culturegraph.org/entityfacts/";
    private static final String EF_URL_SSL = "https://hub.culturegraph.org/entityfacts/";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private EntityFacts() {
    }
    
    /**
     * Checks the current ID of GND data.
     *
     * @param id Only the GND Id (not the complete URI)
     * @return
     */
    public static String getGndId(String id) {

        try {
            final Request request = new Request.Builder()
                    .url(EF_URL_SSL + id)
                    .head()
                    .build();
            final Response response = client.newCall(request).execute();
            return cleanEfId(response.request().url().toString());
        } catch (Exception ex) {
            log.warn("Could not get data from Entity Facts. {}", ex.getMessage());
            return id;
        }
    }

    private static String cleanEfId(String id) {
        if (id.startsWith(EF_URL)) {
            id = id.substring(EF_URL.length());
        } else if (id.startsWith(EF_URL_SSL)) {
            id = id.substring(EF_URL_SSL.length());
        }
        return id;
    }
}
