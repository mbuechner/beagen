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
 * Loads the application's UTF-8 properties on first use.
 * The {@code beagen.dev} system property selects {@code beagen.dev.cfg}
 * when true. Otherwise, properties are loaded from {@code beagen.cfg}.
 *
 * @author Michael Büchner
 */
public final class Configuration {

    private static final String PROPERTY_FILE = "/beagen.cfg";
    private static final String PROPERTY_FILE_DEV = "/beagen.dev.cfg";
    private static final Object LOAD_LOCK = new Object();
    private static Properties properties;

    private Configuration() {
    }

    private static Properties loadProperties() throws IOException {
        final String propertyFile = Boolean.getBoolean("beagen.dev") ? PROPERTY_FILE_DEV : PROPERTY_FILE;
        final InputStream resource = Configuration.class.getResourceAsStream(propertyFile);
        if (resource == null) {
            throw new IOException("Configuration resource not found: " + propertyFile);
        }
        final Properties loaded = new Properties();
        try (final InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            loaded.load(reader);
        }
        System.out.println("Configuration loaded from " + propertyFile);
        return loaded;
    }

    private static Properties properties() throws IOException {
        synchronized (LOAD_LOCK) {
            if (properties == null) {
                properties = loadProperties();
            }
            return properties;
        }
    }

    /**
     * Splits a required property, such as a multi-line BEACON header, into parts.
     *
     * @param key the property name
     * @param split the regular expression used as the separator
     * @return the parts in their original order
     * @throws IOException if the configuration cannot be loaded or the key is missing
     */
    public static String[] getValueAsArray(String key, String split) throws IOException {
        final String value = getValue(key);
        if (value == null) {
            throw new IOException("Missing configuration property: " + key);
        }
        return value.split(split);
    }

    /**
     * Reads the effective value of a property, including any in-memory override.
     *
     * @param key the property name
     * @return the property value, or {@code null} if the key is absent
     * @throws IOException if the configuration cannot be loaded
     */
    public static String getValue(String key) throws IOException {
        return properties().getProperty(key);
    }

    /**
     * Adds or replaces a property for subsequent reads in this process.
     *
     * @param key the property name
     * @param value the new property value
     * @throws IOException if the configuration cannot be loaded
     */
    public static void setValue(String key, String value) throws IOException {
        properties().setProperty(key, value);
    }
}
