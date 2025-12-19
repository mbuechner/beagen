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
import java.io.InputStream;
import java.net.ConnectException;
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
public class DDBApi {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .callTimeout(360, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build();

    private DDBApi() {
    }

    public static InputStream httpGet(final String urlStr) throws ConnectException, IOException {
        try {
            final Request request = new Request.Builder()
                    .url(urlStr)
                    .build();
            final Response response = client.newCall(request).execute();

            // test if request was successful (status 200)
            if (!response.isSuccessful() || response.code() == 403) {
                throw new ConnectException("HTTP status code for " + urlStr + " is " + response.code() + ". " + response.body().string());
            }
            return response.body().byteStream();
        } catch (Exception ex) {
            log.warn("Could not get data from Entity Facts for {}. {}", urlStr, ex.getMessage());
            return null;
        }
    }
}
