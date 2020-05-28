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
package de.ddb.labs.beagen.backend.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ddb.labs.beagen.backend.BeaconFile;
import de.ddb.labs.beagen.backend.BeaconFileController;
import de.ddb.labs.beagen.backend.data.SECTOR;
import de.ddb.labs.beagen.backend.data.TYPE;
import de.ddb.labs.beagen.backend.helper.DDBApi;
import de.ddb.labs.beagen.backend.helper.EntityFacts;
import de.ddb.labs.beagen.backend.helper.EntityManagerUtil;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Michael Büchner
 */
@DisallowConcurrentExecution
public class BeaconJob implements Job {

    // URL of DDB server with dataset ID
    private final static String URL = "https://api.deutsche-digitale-bibliothek.de";
    private final static Map<TYPE, String> SEARCH = new HashMap<>() {
        {
            put(TYPE.PERSON, "/search/person?query=count:*&sort=count_desc");
            put(TYPE.ORGANISATION, "/search/organization?query=count:*&sort=count_desc");

        }
    };
    // count of entities per query
    private final static int ENTITYCOUNT = 1000;
    // Logger
    private static final Logger LOG = LoggerFactory.getLogger(BeaconJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // Date                  
        final Date date = new Date();
        for (TYPE type : TYPE.values()) {
            execute(context, type, SECTOR.values(), date);
        }
    }

    public void execute(JobExecutionContext context, TYPE type, SECTOR[] sectors, Date date) {
        LOG.info("Start BEACON maker job for {}...", type);
        if (SEARCH.get(type) == null || SEARCH.get(type).isEmpty()) {
            LOG.warn("Could not generate search query for type {}. Generation of Beacon file(s) canceled.", type);
            return;
        }

        try {
            // final Map<SECTOR, StringBuilder> beaconSectorFiles = new HashMap<>();
            final Map<SECTOR, ByteArrayOutputStream> byteStreams = new HashMap<>();
            final Map<SECTOR, BufferedWriter> writers = new HashMap<>();

            // count of entities (person, org)
            final Map<SECTOR, Integer> counts = new HashMap<>();

            // init Beacon map
            for (SECTOR sector : sectors) {
                byteStreams.put(sector, new ByteArrayOutputStream());
                writers.put(sector, new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(byteStreams.get(sector)), "UTF-8")));
                //writers.put(sector, new BufferedWriter(new OutputStreamWriter(byteStreams.get(sector), "UTF-8")));
                counts.put(sector, 0);
            }

            final List<EntityCounts> data = getDataFromDdbApi(type);

            LOG.info("Start generating Beacon files of type {}...", type);

            for (final Iterator<EntityCounts> it = data.iterator(); it.hasNext();) {

                final EntityCounts ex = it.next();

                for (SECTOR sector : sectors) {

                    if (ex.hasCount(sector)) {

                        String id = ex.getId();
                        if (id.startsWith("http://d-nb.info/gnd/")) {
                            id = id.substring(21);
                        } else if (id.startsWith("https://d-nb.info/gnd/")) {
                            id = id.substring(22);
                        } else if (id.length() == 32 && !ex.getVariantIds().isEmpty()) {
                            id = EntityFacts.getGndId(ex.getVariantIds().get(0));
                        } else {
                            LOG.warn("Could not get any GND-ID of {}. That should never happen!", id);
                        }

                        counts.put(sector, counts.get(sector) + 1);
                        writers.get(sector).append(id);
                        writers.get(sector).append("|" + ex.getCount(sector));
                        writers.get(sector).newLine();
                    }
                }
            }

            LOG.info("Done generating Beacon files of type {}.", type);

            for (SECTOR sector : sectors) {
                writers.get(sector).close();
                byteStreams.get(sector).flush();
                if (counts.get(sector) > 0) {
                    final BeaconFile files_sector = new BeaconFile();
                    files_sector.setSector(sector);
                    files_sector.setType(type);
                    files_sector.setCreated(date);
                    files_sector.setCount(counts.get(sector));
                    files_sector.setContent(byteStreams.get(sector).toByteArray());

                    final List<BeaconFile> lastBeaconinDatabaseList = BeaconFileController.getBeaconFiles(type, sector, true);
                    if (lastBeaconinDatabaseList.isEmpty() || (lastBeaconinDatabaseList.size() > 0 && !files_sector.equals(lastBeaconinDatabaseList.get(0)))) {
                        LOG.info("Writing {} entities of {} to database. Beacon file size is {}", counts.get(sector), sector.getHumanName(), byteStreams.get(sector).size());
                        final EntityManager em = EntityManagerUtil.getInstance().getEntityManager();
                        final EntityTransaction tx = em.getTransaction();
                        tx.begin();
                        em.persist(files_sector);
                        tx.commit();
                    } else {
                        LOG.warn("Beacon file {}/{} generated is equal to last beacon file in database, so it was NOT written to database.", type, sector);
                    }
                }
                byteStreams.get(sector).close();
            }
        } catch (IOException | PersistenceException ex) {
            LOG.error("Error while processing entity data.", ex);
        } catch (ParseException ex) {
            java.util.logging.Logger.getLogger(BeaconJob.class.getName()).log(Level.SEVERE, null, ex);
        }
        LOG.info("BEACON maker job finished.");
    }

    private List<EntityCounts> getDataFromDdbApi(TYPE type) throws IOException, ParseException {

        final int searchCount = getNumberOfResults(DDBApi.httpGet(URL + SEARCH.get(type), "application/json"));

        // sets are limited to 1000 per search query
        final int iteration = (int) Math.ceil(searchCount / ENTITYCOUNT);

        final List<EntityCounts> list = new ArrayList<>();

        for (int i = 0; i <= iteration; ++i) {
            final String urltmp = URL + SEARCH.get(type) + "&offset=" + (i * ENTITYCOUNT) + "&rows=" + ENTITYCOUNT; // + "&sort=ALPHA_ASC";
            list.addAll(getEntityCounts(DDBApi.httpGet(urltmp, "application/json")));
            LOG.debug("Got {} GND-URIs and kept them in mind. {}", list.size(), urltmp);
        }
        LOG.info("Got {} GND-URIs from DDB API", list.size());
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

                final EntityCounts ec = new EntityCounts(id, count);

                final JsonNode variantIds = objNode.get("variantId");
                for (JsonNode variantId : variantIds) {
                    ec.addVariantIds(variantId.asText());
                }

                for (SECTOR ct : SECTOR.values()) {
                    if (ct != SECTOR.ALL) {
                        try {
                            final int sector_count = objNode.get("sectors").get(ct.getJsonKey()).asInt();
                            ec.addCount(ct, sector_count);
                        } catch (Exception a) {
                            LOG.debug("Could not get {} at {}", ct.getJsonKey(), id, a);
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
        private List<String> variantIds;
        private final Map<SECTOR, Integer> counts;

        EntityCounts(String id, int count) {
            this.id = id;
            this.variantIds = new ArrayList<>();
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

        public List<String> getVariantIds() {
            return variantIds;
        }

        public void setVariantIds(List<String> variantIds) {
            this.variantIds = variantIds;
        }

        public void addVariantIds(String variantId) {
            variantIds.add(variantId);
        }
    }
}
