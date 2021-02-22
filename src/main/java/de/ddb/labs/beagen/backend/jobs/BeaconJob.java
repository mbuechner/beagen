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
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
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
    private static final String URL = "https://api.deutsche-digitale-bibliothek.de";
    private static final EnumMap<TYPE, String> SEARCH = new EnumMap<>(TYPE.class);
    private static final String SECTORS = "sectors";

    static {
        SEARCH.put(TYPE.PERSON, "/search/person?query=count:*&sort=count_desc");
        SEARCH.put(TYPE.ORGANISATION, "/search/organization?query=count:*&sort=count_desc");
    }

    // count of entities per query
    private static final int ENTITYCOUNT = 1000;
    // Logger
    private static final Logger LOG = LoggerFactory.getLogger(BeaconJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // Date                  
        final Date date = new Date();
        for (TYPE type : TYPE.values()) {
            execute(type, SECTOR.values(), date);
        }
    }

    /**
     * Execute the generation of the BEACON files
     *
     * @param type For which types
     * @param sectors For which sectors
     * @param date Date to set for BEACOn files
     */
    public void execute(TYPE type, SECTOR[] sectors, Date date) {
        LOG.info("Start BEACON maker job for {}...", type);
        if (SEARCH.get(type) == null || SEARCH.get(type).isEmpty()) {
            LOG.warn("Could not generate search query for type {}. Generation of Beacon file(s) canceled.", type);
            return;
        }

        try {
            final EnumMap<SECTOR, ByteArrayOutputStream> byteStreams = new EnumMap<>(SECTOR.class);
            final EnumMap<SECTOR, BufferedWriter> writers = new EnumMap<>(SECTOR.class);

            // count of entities (person, org)
            final EnumMap<SECTOR, Integer> counts = new EnumMap<>(SECTOR.class);

            // init Beacon map
            for (SECTOR sector : sectors) {
                byteStreams.put(sector, new ByteArrayOutputStream());
                writers.put(sector, new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(byteStreams.get(sector)), StandardCharsets.UTF_8)));
                counts.put(sector, 0);
            }

            final List<EntityCounts> data = getDataFromDdbApi(type);

            LOG.info("Start generating Beacon files of type {}...", type);

            int count = 0;

            for (final Iterator<EntityCounts> it = data.iterator(); it.hasNext();) {

                if (count == data.size() - 1) {
                    LOG.info("{} data processed: {}/{}", type, (count + 1), data.size());
                } else if (count % 10000 == 0) {
                    LOG.info("{} data processed: {}/{}", type, count, data.size());
                }
                count++;

                final EntityCounts ex = it.next();

                for (SECTOR sector : sectors) {

                    if (ex.hasCount(sector)) {

                        String id = ex.getId();
                        if (id.toLowerCase().startsWith("http://d-nb.info/gnd/")) {
                            id = id.substring(21);
                        } else if (id.toLowerCase().startsWith("https://d-nb.info/gnd/")) {
                            id = id.substring(22);
                        } else if (id.length() == 32
                                && !ex.getVariantIds().isEmpty()
                                && ex.getVariantIds().get(0).toLowerCase().startsWith("http://d-nb.info/gnd/")) {
                            id = EntityFacts.getGndId(ex.getVariantIds().get(0).substring(21));
                        } else if (id.length() == 32
                                && !ex.getVariantIds().isEmpty()
                                && ex.getVariantIds().get(0).toLowerCase().startsWith("https://d-nb.info/gnd/")) {
                            id = EntityFacts.getGndId(ex.getVariantIds().get(0).substring(22));
                        } else {
                            LOG.warn("Could not get any GND-ID of {}. That should never happen!", id);
                            continue;
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
                    if (lastBeaconinDatabaseList.isEmpty() || (lastBeaconinDatabaseList.isEmpty() && !files_sector.equals(lastBeaconinDatabaseList.get(0)))) {
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
        } catch (ParseException | IOException | PersistenceException ex) {
            LOG.error("Error while processing entity data. {}", ex.getMessage());
        }

        LOG.info("BEACON maker job finished.");
    }

    private List<EntityCounts> getDataFromDdbApi(TYPE type) throws IOException, ParseException {

        final int searchCount = getNumberOfResults(DDBApi.httpGet(URL + SEARCH.get(type), "application/json"));

        // sets are limited to 1000 per search query
        final int iteration = (int) Math.ceil((double) searchCount / (double) ENTITYCOUNT);

        final List<EntityCounts> list = new ArrayList<>();

        for (int i = 0; i <= iteration; i++) {

            if (i % 10 == 0) {
                LOG.info("{} data from DDBapi downloaded: {}/{}", type, list.size(), searchCount);
            }

            final String urltmp = URL + SEARCH.get(type) + "&offset=" + (i * ENTITYCOUNT) + "&rows=" + ENTITYCOUNT;

            list.addAll(getEntityCounts(DDBApi.httpGet(urltmp, "application/json")));

            // last logging
            if (i == iteration) {
                LOG.info("{} data from DDBapi downloaded: {}/{}", type, list.size(), searchCount);
            }

            LOG.debug("Got {} GND-URIs and kept them in mind. {}", list.size(), urltmp);
        }
        LOG.info("Got {} GND-URIs from DDB API", list.size());
        return list;
    }

    private static int getNumberOfResults(InputStream searchResult) throws IOException {
        final ObjectMapper m = new ObjectMapper();
        JsonNode resultsNode;
        try {
            resultsNode = m.readTree(searchResult).findValue("numberOfResults");
        } catch (Exception e) {
            return 0;
        }
        return resultsNode.asInt();
    }

    private List<EntityCounts> getEntityCounts(InputStream searchResult) throws IOException {

        final List<EntityCounts> list = new ArrayList<>();
        final ObjectMapper m = new ObjectMapper();

        JsonNode resultsNode;
        try {
            resultsNode = m.readTree(searchResult).get("results").get(0).get("docs");
        } catch (Exception e) {
            return new ArrayList<>();
        }
        if (resultsNode.isArray()) {
            for (final JsonNode objNode : resultsNode) {
                final String id = objNode.get("id").asText();
                LOG.debug("Processing {}...", id);

                final int count = objNode.get("count").asInt();

                final EntityCounts ec = new EntityCounts(id, count);

                final JsonNode variantIds = objNode.get("variantId");
                for (JsonNode variantId : variantIds) {
                    ec.addVariantIds(variantId.asText());
                }

                for (SECTOR ct : SECTOR.values()) {
                    if (ct != SECTOR.ALL) {
                        try {
                            if (objNode.get(SECTORS).has(ct.getJsonKey()) && objNode.get(SECTORS).get(ct.getJsonKey()).isInt()) {
                                final int sector_count = objNode.get(SECTORS).get(ct.getJsonKey()).asInt();

                                LOG.debug("Adding: {} - {}", ct, sector_count);
                                ec.addCount(ct, sector_count);
                            }
                        } catch (Exception a) {
                            LOG.warn("Could not get or save {} at {}. {}", ct.getJsonKey(), id, a.getMessage());
                        }
                    }
                }
                list.add(ec);
            }
        }
        return list;
    }

    private static class EntityCounts {

        private final String id;
        private List<String> variantIds;
        private final EnumMap<SECTOR, Integer> counts;

        EntityCounts(String id, int count) {
            this.id = id;
            this.variantIds = new ArrayList<>();
            this.counts = new EnumMap<>(SECTOR.class);
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

        public Integer addCount(SECTOR ct, int value) {
            return counts.put(ct, value);
        }

        public String getId() {
            return id;
        }

        public EnumMap<SECTOR, Integer> getCounts() {
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
