/* 
 * Copyright 2019-2021 Michael Büchner, Deutsche Digitale Bibliothek
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
 * @author Michael Büchner
 */
public enum TYPE {
    ORGANISATION("Organisation"),
    PERSON("Person");

    private final String name;

    private TYPE(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Json Serializer for output over API
     */
    public static class TypeSerializer extends JsonSerializer<TYPE> {

        @Override
        public void serialize(TYPE t, JsonGenerator jg, SerializerProvider sp) throws IOException {

            final String name = t.getName();

            if ((name != null && !name.isEmpty())) {

                jg.writeStartObject();

                if (t == TYPE.PERSON) {
                    jg.writeFieldName("@id");
                    jg.writeString("http://d-nb.info/standards/elementset/gnd#Person");
                }

                if (t == TYPE.ORGANISATION) {
                    jg.writeFieldName("@id");
                    jg.writeString("http://d-nb.info/standards/elementset/gnd#CorporateBody");
                }

                jg.writeFieldName("abbr");
                jg.writeString(name);

                jg.writeFieldName("name");
                jg.writeString(name);
            }

            jg.writeEndObject();
        }
    }
}
