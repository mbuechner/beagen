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
package de.ddb.labs.beagen.backend.data;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Cultural sectors used to group BEACON entries and filter snapshot history.
 * Each value maps a DDB count field to its display label, download filename component,
 * and configuration key. {@link #ALL} represents its own aggregate snapshot.
 *
 * @author Michael Büchner
 */
public enum SECTOR {
    /** Aggregate entry count across the cultural sectors. */
    ALL("count", "all", "Alle", "all", ""),
    /** Archives. */
    ARCHIVE("count_sec_01", "sec_01", "Archiv", "archive", "http://ddb.vocnet.org/sparte/sparte001"),
    /** Libraries. */
    LIBRARY("count_sec_02", "sec_02", "Bibliothek", "library", "http://ddb.vocnet.org/sparte/sparte002"),
    /** Monument protection. */
    MONUMENTPROTECTION("count_sec_03", "sec_03", "Denkmalpflege", "monument-protection", "http://ddb.vocnet.org/sparte/sparte003"),
    /** Research institutions. */
    RESEARCH("count_sec_04", "sec_04", "Forschung", "research", "http://ddb.vocnet.org/sparte/sparte004"),
    /** Media collections. */
    MEDIA("count_sec_05", "sec_05", "Mediathek", "media", "http://ddb.vocnet.org/sparte/sparte005"),
    /** Museums. */
    MUSEUM("count_sec_06", "sec_06", "Museum", "museum", "http://ddb.vocnet.org/sparte/sparte006"),
    /** The DDB category for other sectors. */
    OTHER("count_sec_07", "sec_07", "Sonstige", "other", "http://ddb.vocnet.org/sparte/sparte007");

    private final String jsonKey, abbr, fileName, humanName, uri;

    private SECTOR(String jsonKey, String abbr, String humanName, String fileName, String uri) {
        this.uri = uri;
        this.jsonKey = jsonKey;
        this.abbr = abbr;
        this.humanName = humanName;
        this.fileName = fileName;
    }

    /**
     * Returns the field name for this sector's count in the DDB import data.
     *
     * @return the source JSON field name
     */
    public String getJsonKey() {
        return jsonKey;
    }

    /**
     * Returns the short sector code used in header configuration and JSON history responses.
     *
     * @return the short sector code
     */
    public String getShortName() {
        return abbr;
    }

    /**
     * Returns the German label displayed in the snapshot history.
     *
     * @return the human-readable sector label
     */
    public String getHumanName() {
        return humanName;
    }

    /**
     * Returns the component used to build a sector-specific download filename.
     *
     * @return the sector's filename component
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the identifier from the DDB cultural-sector vocabulary.
     *
     * @return the vocabulary URI, or an empty string for the aggregate sector
     */
    public String getUri() {
        return uri;
    }

    /**
     * Serializes the sector's short code and label for the history API.
     * The vocabulary URI is included when the sector has one.
     */
    public static class SectorSerializer extends ValueSerializer<SECTOR> {

        @Override
        public void serialize(SECTOR t, JsonGenerator jg, SerializationContext context) {

            final String uri = t.getUri();
            final String abbr = t.getShortName();
            final String name = t.getHumanName();

            if ((uri != null && !uri.isEmpty())
                    || (abbr != null && !abbr.isEmpty())
                    || (name != null && !name.isEmpty())) {

                jg.writeStartObject();

                if (uri != null && !uri.isEmpty()) {
                    jg.writeName("@id");
                    jg.writeString(uri);
                }

                if (abbr != null && !abbr.isEmpty()) {
                    jg.writeName("abbr");
                    jg.writeString(abbr);
                }

                if (name != null && !name.isEmpty()) {
                    jg.writeName("name");
                    jg.writeString(name);
                }

                jg.writeEndObject();
            }
        }
    }
}
