/* 
 * Copyright 2019, 2020 Michael Büchner, Deutsche Digitale Bibliothek
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Michael Büchner
 */
public class EntityFacts {

    private final static String EF_URL = "http://hub.culturegraph.org/entityfacts/";
    // Logger
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(EntityFacts.class);

    /**
     * Checks the current ID of GND data.
     *
     * @param id Only the GND Id (not the complete URI)
     * @return
     */
    public static String getGndId(String id) {

        try {
            final URL url = new URL(EF_URL + cleanId(id));
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            final ObjectMapper m = new ObjectMapper();
            final JsonNode resultsNode = m.readTree(conn.getInputStream()).get("@id");
            final String result = cleanId(resultsNode.asText());
            conn.disconnect();
            return result;
        } catch (IOException ex) {
            LOG.warn("Could not get data from Entity Facts. {}", ex.getMessage());
            return id;
        }
    }

    private static String cleanId(String id) {
        if (id.startsWith("http://d-nb.info/gnd/")) {
            id = id.substring(21);
        } else if (id.startsWith("https://d-nb.info/gnd/")) {
            id = id.substring(22);
        }
        return id;
    }
}
