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
package de.ddb.labs.beagen.backend.jobs;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import de.ddb.labs.beagen.backend.BeaconFile;
import de.ddb.labs.beagen.backend.BeaconFileController;
import de.ddb.labs.beagen.backend.data.SECTOR;
import de.ddb.labs.beagen.backend.data.TYPE;
import de.ddb.labs.beagen.backend.helper.DDBApi;
import de.ddb.labs.beagen.backend.helper.EntityFacts;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import jakarta.persistence.PersistenceException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 *
 * @author Michael Büchner
 */
@Slf4j
@DisallowConcurrentExecution
public class BeaconJob implements Job {

    // URL of DDB server with dataset ID
    private static final String URL = "https://api.deutsche-digitale-bibliothek.de/2";
    private static final EnumMap<TYPE, String> SEARCH = new EnumMap<>(TYPE.class);

    private final JsonMapper mapper = JsonMapper.builderWithJackson2Defaults().build();

    // count of entities per query
    private static final int ENTITYCOUNT = 10000;

    static {
        SEARCH.put(TYPE.PERSON, "/search/index/person/select?"
                + "q=count:*"
                + "&fl=id,variant_id,count,count_sec_01,count_sec_02,count_sec_03,count_sec_04,count_sec_05,count_sec_06,count_sec_07"
                + "&rows=" + ENTITYCOUNT
                + "&sort=count%20DESC,id%20ASC"
                + "&wt=json");
        SEARCH.put(TYPE.ORGANISATION, "/search/index/organization/select?"
                + "q=(type:gnd-organization%20AND%20count:*)%20OR%20(type:ddb-institution%20AND%20variant_id:*)"
                + "&fl=id,variant_id,count,count_sec_01,count_sec_02,count_sec_03,count_sec_04,count_sec_05,count_sec_06,count_sec_07"
                + "&rows=" + ENTITYCOUNT
                + "&sort=count%20DESC,id%20ASC"
                + "&wt=json");
    }

    private static final String NEWSPAPER_SEARCH = "/search/index/newspaper/select?"
            + "q=hasLoadedIssues:true"
            + "&rows=2147483647"
            + "&wt=csv"
            + "&fl=id"
            + "&sort=id ASC";

    /**
     *
     * @param context
     * @throws JobExecutionException
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // Date                  
        final Date date = new Date();
        executeNewspapers(date);
        execute(TYPE.ORGANISATION, SECTOR.values(), date);
        execute(TYPE.PERSON, SECTOR.values(), date);
    }

    public void executeNewspapers(Date date) {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int count = 0;

        try (final InputStream is = DDBApi.httpGet(URL + NEWSPAPER_SEARCH);
                final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(baos), StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            if (header != null && header.startsWith("\uFEFF")) {
                header = header.substring(1);
            }
            if (!"id".equals(header) && !"\"id\"".equals(header)) {
                throw new IOException("Missing or invalid newspaper CSV header");
            }

            for (String line; (line = reader.readLine()) != null;) {
                if (!line.isBlank()) {
                    bw.append(line);
                    bw.newLine();
                    count++;
                }
            }
        } catch (IOException ex) {
            log.error("Could not download newspaper data. {}", ex.getMessage(), ex);
            return;
        }

        if (count == 0) {
            log.info("No newspaper entries returned. Keeping any existing BEACON file.");
            return;
        }

        final BeaconFile bf = new BeaconFile(TYPE.NEWSPAPER, SECTOR.ALL, date, count, baos.toByteArray());
        try {
            final List<BeaconFile> lastBeaconinDatabaseList = BeaconFileController.getBeaconFiles(TYPE.NEWSPAPER, SECTOR.ALL, true);
            if (lastBeaconinDatabaseList.isEmpty() || !bf.equals(lastBeaconinDatabaseList.getFirst())) {
                log.info("Writing {} entities of {} to database. Beacon file size is {}", count, SECTOR.ALL.getHumanName(), baos.size());
                BeaconFileController.saveBeaconFile(bf);
            } else {
                log.warn("Beacon file {}/{} generated is equal to last beacon file in database, so it was NOT written to database.", TYPE.NEWSPAPER, SECTOR.ALL);
            }
        } catch (PersistenceException ex) {
            log.error("Could not write the newspaper Beacon file. {}", ex.getMessage(), ex);
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
        log.info("Start BEACON maker job for {}...", type);
        if (SEARCH.get(type) == null || SEARCH.get(type).isEmpty()) {
            log.warn("Could not generate search query for type {}. Generation of Beacon file(s) canceled.", type);
            return;
        }

        try {
            final List<EntityCounts> data = getDataFromDdbApi(type);
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

            log.info("Start generating Beacon files of type {}...", type);

            int count = 0;

            for (final Iterator<EntityCounts> it = data.iterator(); it.hasNext();) {

                if (count == data.size() - 1) {
                    log.info("{} data processed: {}/{}", type, (count + 1), data.size());
                } else if (count % 10000 == 0) {
                    log.info("{} data processed: {}/{}", type, count, data.size());
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
                                && !ex.getVariant_id().isEmpty()
                                && ex.getVariant_id().get(0).toLowerCase().startsWith("http://d-nb.info/gnd/")) {
                            id = EntityFacts.getGndId(ex.getVariant_id().get(0).substring(21));
                        } else if (id.length() == 32
                                && !ex.getVariant_id().isEmpty()
                                && ex.getVariant_id().get(0).toLowerCase().startsWith("https://d-nb.info/gnd/")) {
                            id = EntityFacts.getGndId(ex.getVariant_id().get(0).substring(22));
                        } else {
                            log.warn("Could not get any GND-ID of {}. That should never happen!", id);
                            continue;
                        }

                        counts.put(sector, counts.get(sector) + 1);
                        writers.get(sector).append(id);
                        writers.get(sector).append("|" + ex.getCount(sector));
                        writers.get(sector).newLine();
                    }
                }
            }

            log.info("Done generating Beacon files of type {}.", type);

            for (SECTOR sector : sectors) {
                writers.get(sector).close();
                if (counts.get(sector) > 0) {
                    final BeaconFile files_sector = new BeaconFile();
                    files_sector.setSector(sector);
                    files_sector.setType(type);
                    files_sector.setCreated(date);
                    files_sector.setCount(counts.get(sector));
                    files_sector.setContent(byteStreams.get(sector).toByteArray());

                    final List<BeaconFile> lastBeaconinDatabaseList = BeaconFileController.getBeaconFiles(type, sector, true);
                    if (lastBeaconinDatabaseList.isEmpty() || !files_sector.equals(lastBeaconinDatabaseList.getFirst())) {
                        log.info("Writing {} entities of {} to database. Beacon file size is {}", counts.get(sector), sector.getHumanName(), byteStreams.get(sector).size());
                        BeaconFileController.saveBeaconFile(files_sector);
                    } else {
                        log.warn("Beacon file {}/{} generated is equal to last beacon file in database, so it was NOT written to database.", type, sector);
                    }
                }
            }
        } catch (IOException | JacksonException | PersistenceException ex) {
            log.error("Error while processing {} entity data. {}", type, ex.getMessage(), ex);
        }

        log.info("BEACON maker job finished.");
    }

    private List<EntityCounts> getDataFromDdbApi(TYPE type) throws IOException {

        final String baseUrl = URL + SEARCH.get(type);

        final List<EntityCounts> list = new ArrayList<>();
        String nextCursorMark = "*";

        while (true) {
            final String url = baseUrl + "&cursorMark=" + URLEncoder.encode(nextCursorMark, StandardCharsets.UTF_8);
            log.debug("Query: {}", url);

            final JsonNode doc;
            try (final InputStream input = DDBApi.httpGet(url)) {
                doc = mapper.readTree(input);
            }
            if (doc == null) {
                throw new IOException("Empty DDB API response for " + type);
            }
            final JsonNode cursor = doc.path("nextCursorMark");
            final JsonNode response = doc.path("response");
            final JsonNode numFound = response.path("numFound");
            final JsonNode docsArray = response.path("docs");
            if (!cursor.isString() || !numFound.isIntegralNumber() || !docsArray.isArray()) {
                throw new IOException("Invalid DDB API response for " + type);
            }
            final String nextCursorMarkLocal = cursor.asString();
            if (nextCursorMarkLocal.equals(nextCursorMark)) {
                nextCursorMark = "";
            } else {
                nextCursorMark = nextCursorMarkLocal;
            }
            final int totalCount = numFound.asInt();
            final List<EntityCounts> ec = Arrays.asList(mapper.treeToValue(docsArray, EntityCounts[].class));
            list.addAll(ec);

            if (list.size() % 10000 == 0) {
                log.info("{} data from DDBapi downloaded: {}/{}", type, list.size(), totalCount);
            } else if (list.size() == totalCount) {
                // last logging
                log.info("{} data from DDBapi downloaded: {}/{}", type, list.size(), list.size());
            }

            if (nextCursorMark.isBlank() || list.size() == totalCount) {
                break;
            }
        }
        log.info("Got {} GND-URIs from DDB API", list.size());
        return list;
    }

    private static class EntityCounts {

        @Getter
        @Setter
        private String id;
        @Getter
        @Setter
        private List<String> variant_id;
        @Getter
        @Setter
        private int count;
        @Getter
        @Setter
        private int count_sec_01;
        @Getter
        @Setter
        private int count_sec_02;
        @Getter
        @Setter
        private int count_sec_03;
        @Getter
        @Setter
        private int count_sec_04;
        @Getter
        @Setter
        private int count_sec_05;
        @Getter
        @Setter
        private int count_sec_06;
        @Getter
        @Setter
        private int count_sec_07;

        // Used by Jackson during deserialization
        @SuppressWarnings("unused")
        public EntityCounts() {
            this.variant_id = new ArrayList<>();
        }

        @SuppressWarnings("unused")
        public EntityCounts(String id, int count) {
            this.id = id;
            this.count = count;
            this.variant_id = new ArrayList<>();
        }

        public boolean hasCount(SECTOR ct) {
            switch (ct) {
                case ALL -> {
                    if (count > 0) {
                        return true;
                    }
                }
                case ARCHIVE -> {
                    if (count_sec_01 > 0) {
                        return true;
                    }
                }
                case LIBRARY -> {
                    if (count_sec_02 > 0) {
                        return true;
                    }
                }
                case MONUMENTPROTECTION -> {
                    if (count_sec_03 > 0) {
                        return true;
                    }
                }
                case RESEARCH -> {
                    if (count_sec_04 > 0) {
                        return true;
                    }
                }
                case MEDIA -> {
                    if (count_sec_05 > 0) {
                        return true;
                    }
                }
                case MUSEUM -> {
                    if (count_sec_06 > 0) {
                        return true;
                    }
                }
                case OTHER -> {
                    if (count_sec_07 > 0) {
                        return true;
                    }
                }
                default -> {
                }
            }

            return false;
        }

        public int getCount(SECTOR ct) {
            switch (ct) {
                case ALL -> {
                    return count;
                }
                case ARCHIVE -> {
                    return count_sec_01;
                }
                case LIBRARY -> {
                    return count_sec_02;
                }
                case MONUMENTPROTECTION -> {
                    return count_sec_03;
                }
                case RESEARCH -> {
                    return count_sec_04;
                }
                case MEDIA -> {
                    return count_sec_05;
                }
                case MUSEUM -> {
                    return count_sec_06;
                }
                case OTHER -> {
                    return count_sec_07;
                }
                default -> {
                    return 0;
                }
            }
        }
    }
}
