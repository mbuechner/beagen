/*
 * Copyright 2017 Michael Büchner.
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
package de.ddb.labs.beagen.helper;

import java.io.IOException;
import java.io.InputStream;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

public class Configuration {

	private final static String PROPERTY_FILE = "/beagen.cfg.xml";
	private final static Configuration INSTANCE = new Configuration();
	private final static Properties PROPERTIES = new Properties();

	private Configuration() {
	}

	public static Configuration getInstance() throws InvalidPropertiesFormatException, IOException {

		if (PROPERTIES.isEmpty()) {
                    try (final InputStream cfg = Configuration.class.getResourceAsStream(PROPERTY_FILE)) {
                        PROPERTIES.loadFromXML(cfg);
                    }
		}

		return Configuration.INSTANCE;
	}
	
	public String getValue(String key) {
		if (PROPERTIES == null)
			return null;
		return PROPERTIES.getProperty(key);
	}

	public String getProperty(String key) {
		if (PROPERTIES == null)
			return null;
		return PROPERTIES.getProperty(key);
	}
}