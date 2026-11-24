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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Streams search results from the Deutsche Digitale Bibliothek API.
 *
 * @author Michael Büchner
 */
public class DDBApi {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .callTimeout(360, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build();

    private DDBApi() {
    }

    /**
     * Sends a GET request and exposes the response body as a stream.
     * The caller must close the stream to release the HTTP response and unregister
     * the request from the import's cancellation tracker.
     *
     * @param url the complete request URL, including any query parameters
     * @param cancellation the cancellation tracker for the import
     * @return the body of a successful HTTP response
     * @throws IOException if the request fails, is cancelled, or returns a non-success status
     * @throws IllegalStateException if the tracker already has an active request
     */
    public static InputStream httpGet(String url, ImportCancellation cancellation) throws IOException {
        final Request request = new Request.Builder()
                .url(url)
                .build();
        final Call call = client.newCall(request);
        cancellation.begin(call);
        try {
            final Response response = call.execute();
            if (!response.isSuccessful()) {
                try (response) {
                    throw new IOException("HTTP status code for " + url + " is " + response.code());
                }
            }
            // The synchronous call stops being dispatched after headers, but the body is still being read.
            return new FilterInputStream(response.body().byteStream()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        cancellation.finish(call);
                    }
                }
            };
        } catch (IOException | RuntimeException e) {
            cancellation.finish(call);
            throw e;
        }
    }
}
