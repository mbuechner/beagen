/*
 * Copyright 2019 buechner.
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 *
 * @author buechner
 */
public enum SECTOR {
    ALL("count", "all", "Alle", "all", ""),
    SEC_01("count_sec_01", "sec_01", "Archiv", "archive", "http://ddb.vocnet.org/sparte/sparte001"),
    SEC_02("count_sec_02", "sec_02", "Bibliothek", "library", "http://ddb.vocnet.org/sparte/sparte002"),
    SEC_03("count_sec_03", "sec_03", "Denkmalpflege", "monument-protection", "http://ddb.vocnet.org/sparte/sparte003"),
    SEC_04("count_sec_04", "sec_04", "Forschung", "research", "http://ddb.vocnet.org/sparte/sparte004"),
    SEC_05("count_sec_05", "sec_05", "Mediathek", "media", "http://ddb.vocnet.org/sparte/sparte005"),
    SEC_06("count_sec_06", "sec_06", "Museum", "museum", "http://ddb.vocnet.org/sparte/sparte006"),
    SEC_07("count_sec_07", "sec_07", "Sonstige", "other", "http://ddb.vocnet.org/sparte/sparte007");

    private final String name, shortName, fileName, humanName, uri;

    private SECTOR(String name, String shortName, String humanName, String fileName, String uri) {
        this.uri = uri;
        this.name = name;
        this.shortName = shortName;
        this.humanName = humanName;
        this.fileName = fileName;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getHumanName() {
        return humanName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getUri() {
        return uri;
    }

    /**
     * Json Serializer for output over API
     */
    public static class SectorSerializer extends JsonSerializer<SECTOR> {

        @Override
        public void serialize(SECTOR t, JsonGenerator jg, SerializerProvider sp) throws IOException {

            final String uri = t.getUri();
            final String abbr = t.getShortName();
            final String name = t.getHumanName();

            if ((uri != null && !uri.isEmpty())
                    || (name != null && !name.isEmpty())
                    || (name != null && !name.isEmpty())) {

                jg.writeStartObject();

                if (uri != null && !uri.isEmpty()) {
                    jg.writeFieldName("@id");
                    jg.writeString(uri);
                }

                if (abbr != null && !abbr.isEmpty()) {
                    jg.writeFieldName("abbr");
                    jg.writeString(abbr);
                }

                if (name != null && !name.isEmpty()) {
                    jg.writeFieldName("name");
                    jg.writeString(name);
                }

                jg.writeEndObject();
            }
        }
    }
}
