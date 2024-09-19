/* 
 * Copyright 2019-2024 Michael Büchner, Deutsche Digitale Bibliothek
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

/**
 *
 * @author Michael Büchner
 */
public class Configuration {

    private static final String PROPERTY_FILE = "/beagen.cfg";
    private static final String PROPERTY_FILE_DEV = "/beagen.dev.cfg";
    private static final Properties PROPERTIES = new Properties();

    private Configuration() {
    }

    private static class ConfigurationHelper {
        private static final Configuration INSTANCE = new Configuration();
    }

    public static Configuration get() throws InvalidPropertiesFormatException, IOException {

        if (PROPERTIES.isEmpty()) {
            // first try to load development properties
            try (final BufferedReader cfg = new BufferedReader(new InputStreamReader(Configuration.class.getResourceAsStream(PROPERTY_FILE_DEV), StandardCharsets.UTF_8))) {
                PROPERTIES.load(cfg);
                System.out.println("Configuration loaded from " + PROPERTY_FILE_DEV);
            } catch (Exception e) {
                try (final BufferedReader cfg = new BufferedReader(new InputStreamReader(Configuration.class.getResourceAsStream(PROPERTY_FILE), StandardCharsets.UTF_8))) {
                    PROPERTIES.load(cfg);
                    System.out.println("Configuration loaded from " + PROPERTY_FILE);
                } catch (Exception ex) {
                    System.err.println("Could NOT load configuration from "
                            + PROPERTY_FILE_DEV + " (" + e.getMessage() + ") and "
                            + PROPERTY_FILE + " (" + ex.getMessage() + ").");
                }
            }
        }

        return ConfigurationHelper.INSTANCE;
    }

    public String[] getValueAsArray(String key, String split) {
        return PROPERTIES.getProperty(key).split(split);
    }

    public String getValue(String key) {
        return PROPERTIES.getProperty(key);
    }

    public void setValue(String key, String value) {
        PROPERTIES.setProperty(key, value);
    }
}
