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
package de.ddb.labs.beagen;

import de.ddb.labs.beagen.backend.data.SECTOR;
import de.ddb.labs.beagen.backend.data.TYPE;
import de.ddb.labs.beagen.backend.helper.EntityFacts;
import de.ddb.labs.beagen.backend.jobs.BeaconJob;
import java.util.Date;

/**
 *
 * @author Michael Büchner <m.buechner@dnb.de>
 */
public class TestMain {

    public static void main(String[] args) throws Exception {

        System.out.println("124645097 -> " + EntityFacts.getGndId("124645097"));
        System.out.println("118540238 -> " + EntityFacts.getGndId("118540238"));
        
        new BeaconJob().execute(TYPE.PERSON, SECTOR.values(), new Date());
    }
}
