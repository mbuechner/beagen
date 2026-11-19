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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 *
 * @author Michael Büchner
 */
public class Configuration {

    private static final String PROPERTY_FILE = "/beagen.cfg";
    private static final String PROPERTY_FILE_DEV = "/beagen.dev.cfg";
    private static Configuration instance;
    private final Properties properties = new Properties();

    private Configuration() throws IOException {
        final String propertyFile = Boolean.getBoolean("beagen.dev") ? PROPERTY_FILE_DEV : PROPERTY_FILE;
        final InputStream resource = Configuration.class.getResourceAsStream(propertyFile);
        if (resource == null) {
            throw new IOException("Configuration resource not found: " + propertyFile);
        }
        try (final InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        System.out.println("Configuration loaded from " + propertyFile);
    }

    public static synchronized Configuration get() throws IOException {
        if (instance == null) {
            instance = new Configuration();
        }
        return instance;
    }

    public String[] getValueAsArray(String key, String split) {
        return properties.getProperty(key).split(split);
    }

    public String getValue(String key) {
        return properties.getProperty(key);
    }

    public void setValue(String key, String value) {
        properties.setProperty(key, value);
    }
}
