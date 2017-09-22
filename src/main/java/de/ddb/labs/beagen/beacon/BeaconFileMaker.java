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
package de.ddb.labs.beagen.beacon;

import de.ddb.labs.beagen.helper.EntityManagerUtil;
import de.ddb.labs.beagen.helper.DatabaseHelper;
import de.ddb.labs.beagen.helper.DDBApiHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ddb.labs.beagen.beacon.BeaconFile.SECTOR;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeaconFileMaker {

    // Singeton
    private static BeaconFileMaker instance;

    // URL of DDB server with dataset ID
    final static String URL = "https://api.deutsche-digitale-bibliothek.de";
    // search string
    final static String SEARCH = "/entities?query=count:*&sort=count_desc";
    // count of entities per query
    final static int ENTITYCOUNT = 1000;
    // Logger
    private static final Logger LOG = LoggerFactory.getLogger(BeaconFileMaker.class);

    private static final String[] BEACONHEADER = new String[]{
        "#FORMAT: BEACON",
        "#PREFIX: http://d-nb.info/gnd/",
        "#TARGET: https://www.deutsche-digitale-bibliothek.de/person/gnd/{ID}",
        "#FEED: https://www.deutsche-digitale-bibliothek.de/beacon/{{filename}}",
        "#CONTACT: Deutsche Digitale Bibliothek <daten@deutsche-digitale-bibliothek.de>",
        "#INSTITUTION: Deutsche Digitale Bibliothek",
        "#DESCRIPTION: Personenseiten {{sparte}}in der Deutschen Digitalen Bibliothek",
        "#TIMESTAMP: {{date}}",
        "#UPDATE: weekly"
    };

    private BeaconFileMaker() {
    }

    public static synchronized BeaconFileMaker getInstance() {
        if (BeaconFileMaker.instance == null) {
            BeaconFileMaker.instance = new BeaconFileMaker();
        }
        return BeaconFileMaker.instance;
    }

    public void run() {
        run(SECTOR.values());
    }

    public void run(SECTOR[] sectors) {
        LOG.info("Getting data from DDB API...");
        try {
            // final Map<SECTOR, StringBuilder> beaconSectorFiles = new HashMap<>();
            final Map<SECTOR, ByteArrayOutputStream> gzipByteStreams = new HashMap<>();
            final Map<SECTOR, BufferedWriter> gzipWriters = new HashMap<>();

            // count of entities (person, org)
            final Map<SECTOR, Integer> counts = new HashMap<>();

            // init Beacon map
            for (SECTOR sector : sectors) {
                gzipByteStreams.put(sector, new ByteArrayOutputStream());
                gzipWriters.put(sector, new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(gzipByteStreams.get(sector)), "UTF-8")));
                counts.put(sector, 0);
            }

            // Date            
            final Date date = new Date();

            // write Headers
            for (SECTOR sector : sectors) {

                final String entityType = "-persons";
                final String fn = "beacon-ddb" + entityType + sector.getFileName() + ".txt";
                final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                sdf.setTimeZone(TimeZone.getDefault());
                final String dt = sdf.format(date);
                for (String s : BEACONHEADER) {
                    s = s.replace("{{date}}", dt);
                    s = s.replace("{{sparte}}", sector.getBeaconDescName());
                    s = s.replace("{{filename}}", fn);
                    gzipWriters.get(sector).append(s);
                    gzipWriters.get(sector).newLine();
                }
            }

            //
            final List<EntityCounts> data = getDataFromDdbApi();

            for (final Iterator<EntityCounts> it = data.iterator(); it.hasNext();) {

                final EntityCounts ex = it.next();

                for (SECTOR sector : sectors) {
                    if (ex.hasCount(sector)) {
                        counts.put(sector, counts.get(sector) + 1);
                        gzipWriters.get(sector).append(ex.getId().substring(21));
                        gzipWriters.get(sector).append("||" + ex.getCount(sector));
                        gzipWriters.get(sector).newLine();
                    }
                }
            }

            for (SECTOR sector : sectors) {
                gzipWriters.get(sector).close();
                gzipByteStreams.get(sector).flush();
                if (counts.get(sector) > 0) {
                    final BeaconFile files_sector = new BeaconFile();
                    files_sector.setType(sector);
                    files_sector.setCreated(date);
                    files_sector.setCount(counts.get(sector));
                    files_sector.setContent(gzipByteStreams.get(sector).toByteArray());

                    final BeaconFile lastBeaconinDatabase = DatabaseHelper.getLastBeaconFile(sector);
                    if (!files_sector.equals(lastBeaconinDatabase)) {
                        LOG.info("Writing " + counts.get(sector) + " entities of " + sector.getHumanName() + " to database. Beacon file size is " + gzipByteStreams.get(sector).size());
                        final EntityManager em = EntityManagerUtil.getInstance().getEntityManager();
                        final EntityTransaction tx = em.getTransaction();
                        tx.begin();
                        em.persist(files_sector);
                        tx.commit();
                    } else {
                        LOG.warn("BEACON file generated is equal to last beacon file in database. New BEACON file was NOT written to database.");
                    }
                }
                gzipByteStreams.get(sector).close();
            }
        } catch (IOException | ParseException | PersistenceException ex) {
            LOG.error("Error while processing entity data.", ex);
        }
    }

    private List<EntityCounts> getDataFromDdbApi() throws IOException, ParseException {

        final int searchCount = getNumberOfResults(DDBApiHelper.httpGet(URL + SEARCH, "application/json"));
        LOG.info("Start to reading " + searchCount + " entities...");

        // sets are limited to 1000 per search query
        final int iteration = (int) Math.ceil(searchCount / ENTITYCOUNT);
        // final int iteration = 3;
        LOG.info("Getting data from search result...");

        final List<EntityCounts> list = new ArrayList<>();

        for (int i = 0; i <= iteration; ++i) {
            final String urltmp = URL + SEARCH + "&offset=" + (i * ENTITYCOUNT) + "&rows=" + ENTITYCOUNT; // + "&sort=ALPHA_ASC";
            LOG.info("Process search results from " + urltmp + "...");
            list.addAll(getEntityCounts(DDBApiHelper.httpGet(urltmp, "application/json")));
            LOG.info("Got " + list.size() + " GND-URIs and saved them to memory.");
        }
        LOG.info("Done.");
        return list;
    }

    private static int getNumberOfResults(InputStream searchResult) throws IOException, ParseException {
        final ObjectMapper m = new ObjectMapper();
        final JsonNode resultsNode = m.readTree(searchResult).findValue("numberOfResults");
        return resultsNode.asInt();
    }

    private List<EntityCounts> getEntityCounts(InputStream searchResult) throws IOException, ParseException {

        final List<EntityCounts> list = new ArrayList<>();
        final ObjectMapper m = new ObjectMapper();
        final JsonNode resultsNode = m.readTree(searchResult).get("results").get(0).get("docs");
        if (resultsNode.isArray()) {
            for (final JsonNode objNode : resultsNode) {
                final String id = objNode.get("id").asText();
                final int count = objNode.get("count").asInt();
                EntityCounts ec = new EntityCounts(id, count);
                for (SECTOR ct : SECTOR.values()) {
                    if (ct != SECTOR.ALL) {
                        try {
                            final int sector_count = objNode.get("sectors").get(ct.getName()).asInt();
                            ec.addCount(ct, sector_count);
                        } catch (Exception a) {
                            LOG.debug("Could not get " + ct.getName() + " at " + id);
                        }
                    }
                }
                list.add(ec);
            }
        }
        return list;
    }

    private class EntityCounts {

        private final String id;
        private final Map<SECTOR, Integer> counts;

        EntityCounts(String id, int count) {
            this.id = id;
            this.counts = new HashMap<>();
            this.counts.put(SECTOR.ALL, count);

        }

        public int getCount() {
            return counts.get(SECTOR.ALL);
        }

        public boolean hasCount(SECTOR ct) {
            return counts.containsKey(ct);
        }

        public int getCount(SECTOR ct) {
            return counts.get(ct);
        }

        public int addCount(SECTOR ct, int value) {
            return counts.put(ct, value);
        }

        public String getId() {
            return id;
        }

        public Map<SECTOR, Integer> getCounts() {
            return counts;
        }
    }
}
