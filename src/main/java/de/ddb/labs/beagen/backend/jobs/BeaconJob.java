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

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import de.ddb.labs.beagen.backend.BeaconFile;
import de.ddb.labs.beagen.backend.BeaconFileController;
import de.ddb.labs.beagen.backend.data.SECTOR;
import de.ddb.labs.beagen.backend.data.TYPE;
import de.ddb.labs.beagen.backend.helper.DDBApi;
import de.ddb.labs.beagen.backend.helper.EntityFacts;
import de.ddb.labs.beagen.backend.helper.ImportCancellation;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import jakarta.persistence.PersistenceException;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imports newspaper titles, organisations, and people from the Deutsche Digitale
 * Bibliothek and saves new or changed BEACON files.
 * Person and organisation imports require a complete set of search results before
 * generating files for the individual cultural sectors.
 *
 * @author Michael Büchner
 */
@DisallowConcurrentExecution
public class BeaconJob implements Job {

    /** Scheduler context key for the shared {@link ImportCancellation} tracker. */
    public static final String CANCELLATION_KEY = "beagen.importCancellation";
    private static final Logger log = LoggerFactory.getLogger(BeaconJob.class);
    private static final List<String> GND_PREFIXES = List.of("http://d-nb.info/gnd/", "https://d-nb.info/gnd/");

    private static final String URL = "https://api.deutsche-digitale-bibliothek.de/2";
    private static final EnumMap<TYPE, String> SEARCH = new EnumMap<>(TYPE.class);

    private final JsonMapper mapper = JsonMapper.builderWithJackson2Defaults().build();

    // Maximum number of entities requested per cursor page.
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
     * Creates an import job for Quartz to schedule.
     */
    public BeaconJob() {
    }

    /**
     * Runs the newspaper, organisation, and person imports in order, assigning
     * the same timestamp to the files created in this run.
     * Cancellation stops the remaining imports without undoing files already saved.
     *
     * @param context the job context whose scheduler provides the cancellation tracker
     * @throws JobExecutionException if the scheduler context cannot be read or has no tracker
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        final ImportCancellation cancellation;
        try {
            cancellation = (ImportCancellation) context.getScheduler().getContext().get(CANCELLATION_KEY);
        } catch (SchedulerException e) {
            throw new JobExecutionException(e);
        }
        if (cancellation == null) {
            throw new JobExecutionException("Import cancellation context is missing");
        }
        final Date date = new Date();
        try {
            executeNewspapers(date, cancellation);
            execute(TYPE.ORGANISATION, SECTOR.values(), date, cancellation);
            execute(TYPE.PERSON, SECTOR.values(), date, cancellation);
        } catch (InterruptedIOException e) {
            log.info("BEACON import cancelled.");
        }
    }

    private void executeNewspapers(Date date, ImportCancellation cancellation) throws InterruptedIOException {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int count = 0;

        try (final InputStream is = DDBApi.httpGet(URL + NEWSPAPER_SEARCH, cancellation);
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
                cancellation.check();
                if (!line.isBlank()) {
                    bw.append(line);
                    bw.newLine();
                    count++;
                }
            }
        } catch (IOException ex) {
            cancellation.check();
            log.error("Could not download newspaper data. {}", ex.getMessage(), ex);
            return;
        }

        if (count == 0) {
            log.info("No newspaper entries returned. Keeping any existing BEACON file.");
            return;
        }

        final BeaconFile bf = new BeaconFile(TYPE.NEWSPAPER, SECTOR.ALL, date, count, baos.toByteArray());
        try {
            if (isChanged(bf)) {
                cancellation.check();
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
     * Resolves each entity's GND identifier once and reuses it for every sector.
     * Saves the nonempty, changed files for this type together after processing all sectors.
     */
    private void execute(TYPE type, SECTOR[] sectors, Date date, ImportCancellation cancellation) throws InterruptedIOException {
        log.info("Start BEACON maker job for {}...", type);
        if (SEARCH.get(type) == null || SEARCH.get(type).isEmpty()) {
            log.warn("Could not generate search query for type {}. Generation of Beacon file(s) canceled.", type);
            return;
        }

        try {
            final List<ResolvedEntity> data = resolveEntities(getDataFromDdbApi(type, cancellation), sectors, cancellation);
            final List<BeaconFile> changed = new ArrayList<>();
            log.info("Start generating Beacon files of type {}...", type);
            for (SECTOR sector : sectors) {
                final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int count = 0;
                try (var writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(bytes), StandardCharsets.UTF_8))) {
                    for (ResolvedEntity entity : data) {
                        cancellation.check();
                        final int sectorCount = entity.counts().getCount(sector);
                        if (sectorCount > 0) {
                            writer.append(entity.id()).append('|').append(Integer.toString(sectorCount)).append('\n');
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    final BeaconFile file = new BeaconFile(type, sector, date, count, bytes.toByteArray());
                    if (isChanged(file)) {
                        changed.add(file);
                    } else {
                        log.info("BEACON file {}/{} is up to date.", type, sector);
                    }
                }
            }
            cancellation.check();
            BeaconFileController.saveBeaconFiles(changed);
            log.info("Saved {} new {} BEACON files.", changed.size(), type);
        } catch (IOException | JacksonException | PersistenceException ex) {
            cancellation.check();
            log.error("Error while processing {} entity data. {}", type, ex.getMessage(), ex);
        }

        log.info("BEACON maker job finished.");
    }

    /**
     * Compares the generated data with the latest file for the same type and sector.
     * A missing or unreadable previous file permits a new history entry.
     */
    private static boolean isChanged(BeaconFile file) {
        final List<BeaconFile> latest = BeaconFileController.getBeaconFiles(file.getType(), file.getSector(), true);
        if (latest.isEmpty()) {
            return true;
        }
        try {
            return !file.hasSameData(latest.getFirst());
        } catch (IOException ex) {
            log.warn("Could not read the previous BEACON file for {}/{}. Saving a new one.", file.getType(), file.getSector(), ex);
            return true;
        }
    }

    private static List<ResolvedEntity> resolveEntities(List<EntityCounts> data, SECTOR[] sectors,
            ImportCancellation cancellation) throws InterruptedIOException {
        final List<ResolvedEntity> resolved = new ArrayList<>(data.size());
        for (EntityCounts entity : data) {
            cancellation.check();
            if (Arrays.stream(sectors).noneMatch(sector -> entity.getCount(sector) > 0)) {
                continue;
            }
            final String id = resolveGndId(entity, cancellation);
            if (id != null) {
                resolved.add(new ResolvedEntity(id, entity));
            } else {
                log.warn("Could not get a GND-ID for {}.", entity.id());
            }
        }
        return resolved;
    }

    private static String resolveGndId(EntityCounts entity, ImportCancellation cancellation) throws InterruptedIOException {
        final String directId = extractGndId(entity.id());
        if (directId != null || entity.id().length() != 32 || entity.variantIds() == null) {
            return directId;
        }
        for (String variant : entity.variantIds()) {
            final String variantId = extractGndId(variant);
            if (variantId != null) {
                final String canonicalId = EntityFacts.getGndId(variantId, cancellation);
                return validGndId(canonicalId) ? canonicalId : variantId;
            }
        }
        return null;
    }

    private static String extractGndId(String uri) {
        if (uri != null) {
            for (String prefix : GND_PREFIXES) {
                if (uri.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    final String id = uri.substring(prefix.length());
                    return validGndId(id) ? id : null;
                }
            }
        }
        return null;
    }

    private static boolean validGndId(String id) {
        return !id.isEmpty() && id.chars().allMatch(ch -> ch >= '0' && ch <= '9' || ch == 'X' || ch == 'x' || ch == '-');
    }

    @FunctionalInterface
    interface PageFetcher {
        InputStream get(String url) throws IOException;
    }

    private List<EntityCounts> getDataFromDdbApi(TYPE type, ImportCancellation cancellation) throws IOException {
        return readPages(URL + SEARCH.get(type), mapper, url -> DDBApi.httpGet(url, cancellation));
    }

    /**
     * Reads cursor pages until the number of unique entities matches the reported total.
     * Incomplete results are rejected so the importer cannot publish a truncated feed.
     *
     * @param baseUrl the search URL with query parameters, excluding the cursor
     * @param mapper the JSON reader for DDB search results
     * @param fetcher opens each page's body, which this method closes after reading
     * @return the complete result in search order
     * @throws IOException if fetching fails or the response is invalid, incomplete, or inconsistent
     */
    static List<EntityCounts> readPages(String baseUrl, JsonMapper mapper, PageFetcher fetcher) throws IOException {
        final List<EntityCounts> entities = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        final Set<String> cursors = new HashSet<>();
        String cursorMark = "*";
        long expectedTotal = -1;
        while (cursors.add(cursorMark)) {
            final String url = baseUrl + "&cursorMark=" + URLEncoder.encode(cursorMark, StandardCharsets.UTF_8);
            final JsonNode doc;
            try (InputStream input = fetcher.get(url)) {
                doc = mapper.readTree(input);
            }
            if (doc == null) {
                throw new IOException("Empty DDB API response");
            }
            final JsonNode cursor = doc.path("nextCursorMark");
            final JsonNode response = doc.path("response");
            final JsonNode numFound = response.path("numFound");
            final JsonNode docs = response.path("docs");
            final JsonNode partial = doc.path("responseHeader").path("partialResults");
            if (!cursor.isString() || !numFound.isIntegralNumber() || !docs.isArray()
                    || !partial.isMissingNode() && (!partial.isBoolean() || partial.asBoolean())) {
                throw new IOException("Invalid or partial DDB API response");
            }
            final long total = numFound.asLong();
            if (total < 0) {
                throw new IOException("Invalid DDB API result count: " + total);
            }
            if (expectedTotal != -1 && total != expectedTotal) {
                throw new IOException("DDB API result count changed during the import: " + expectedTotal + " to " + total);
            }
            expectedTotal = total;
            for (EntityCounts entity : mapper.treeToValue(docs, EntityCounts[].class)) {
                if (entity == null || entity.id() == null || entity.id().isBlank() || !ids.add(entity.id())) {
                    throw new IOException("Missing or repeated entity ID in DDB API results");
                }
                entities.add(entity);
            }

            if (entities.size() == total) {
                log.info("Downloaded {} entities from DDB API.", entities.size());
                return entities;
            }
            final String nextCursor = cursor.asString();
            if (entities.size() > total || docs.isEmpty() || nextCursor.isBlank() || nextCursor.equals(cursorMark)) {
                throw new IOException("Incomplete DDB API results: received " + entities.size() + " of " + total);
            }
            cursorMark = nextCursor;
        }
        throw new IOException("DDB API cursor returned to a previously visited page");
    }

    record EntityCounts(
            String id,
            @JsonProperty("variant_id") List<String> variantIds,
            int count,
            @JsonProperty("count_sec_01") int archive,
            @JsonProperty("count_sec_02") int library,
            @JsonProperty("count_sec_03") int monumentProtection,
            @JsonProperty("count_sec_04") int research,
            @JsonProperty("count_sec_05") int media,
            @JsonProperty("count_sec_06") int museum,
            @JsonProperty("count_sec_07") int other) {

        int getCount(SECTOR sector) {
            return switch (sector) {
                case ALL -> count;
                case ARCHIVE -> archive;
                case LIBRARY -> library;
                case MONUMENTPROTECTION -> monumentProtection;
                case RESEARCH -> research;
                case MEDIA -> media;
                case MUSEUM -> museum;
                case OTHER -> other;
            };
        }
    }

    private record ResolvedEntity(String id, EntityCounts counts) {
    }
}
