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

import de.ddb.labs.beagen.backend.BeaconFile;
import de.ddb.labs.beagen.backend.BeaconFileController;
import de.ddb.labs.beagen.backend.helper.Configuration;
import de.ddb.labs.beagen.backend.helper.EntityManagerUtil;
import de.ddb.labs.beagen.backend.helper.ImportCancellation;
import de.ddb.labs.beagen.backend.jobs.BeaconJob;
import de.ddb.labs.beagen.backend.data.SECTOR;
import de.ddb.labs.beagen.backend.data.TYPE;
import io.javalin.Javalin;
import io.javalin.compression.Brotli;
import io.javalin.compression.CompressionStrategy;
import io.javalin.compression.Gzip;
import io.javalin.config.RoutesConfig;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotAcceptableResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson3;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

/**
 * Starts Beagen's Javalin server and schedules BEACON imports with Quartz.
 * The server serves HTML and JSON history and exposes individual and latest
 * BEACON downloads under the configured URL prefix.
 *
 * @author Michael Büchner
 */
public class Main {

    // Configuration
    private static final String BEAGEN_DATABASE = "beagen.database";
    private static final String BEAGEN_BASEURL = "beagen.baseurl";
    private static final String BEAGEN_PORT = "beagen.port";
    private static final String BEAGEN_PATHPREFIX = "beagen.pathprefix";
    private static final String BEAGEN_CRON = "beagen.cron";

    /**
     * Applies environment overrides, starts the application, and registers its shutdown hook.
     *
     * @param args command-line arguments reserved for the Java entry point
     * @throws Exception if the configuration cannot be loaded or the application cannot start
     */
    public static void main(String[] args) throws Exception {
        for (String key : List.of(BEAGEN_DATABASE, BEAGEN_BASEURL, BEAGEN_PATHPREFIX, BEAGEN_PORT, BEAGEN_CRON)) {
            final String override = System.getenv(key);
            if (override != null) {
                Configuration.setValue(key, override);
            }
        }
        final Javalin app = start();
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop, "beagen-shutdown"));
    }

    /**
     * Initializes persistence and cleans up empty historical files before serving requests.
     * The import schedule is enabled once Javalin has started. Shutdown cancels and joins the active import
     * before closing persistence. Callers that start the server directly own its lifecycle.
     *
     * @return the running server
     * @throws Exception if a startup step fails, with any cleanup failures suppressed
     */
    static Javalin start() throws Exception {
        final String database = Configuration.getValue(BEAGEN_DATABASE);
        System.setProperty(BEAGEN_DATABASE, database);

        final int port = Integer.parseInt(Configuration.getValue(BEAGEN_PORT));
        final String pathPrefix = Configuration.getValue(BEAGEN_PATHPREFIX);
        final String cron = Configuration.getValue(BEAGEN_CRON);
        final var schedule = CronScheduleBuilder.cronSchedule(cron);

        System.out.println(BEAGEN_DATABASE + "=" + database.split(";", 2)[0]);
        System.out.println(BEAGEN_BASEURL + "=" + Configuration.getValue(BEAGEN_BASEURL));
        System.out.println(BEAGEN_PATHPREFIX + "=" + pathPrefix);
        System.out.println(BEAGEN_PORT + "=" + port);
        System.out.println(BEAGEN_CRON + "=" + cron);

        final JobDetail job = JobBuilder.newJob(BeaconJob.class)
                .withIdentity("cronjob", "crongroup")
                .build();
        final Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("crontrigger", "crongroup")
                .startNow()
                .withSchedule(schedule)
                .build();

        final ResponseCache responseCache = new ResponseCache();
        final EntityManagerUtil entityManagerUtil = EntityManagerUtil.initialize();
        final ImportCancellation cancellation = new ImportCancellation();
        Scheduler scheduler = null;
        Javalin app = null;
        try {
            scheduler = StdSchedulerFactory.getDefaultScheduler();
            scheduler.getContext().put(BeaconJob.CANCELLATION_KEY, cancellation);
            scheduler.scheduleJob(job, trigger);
            final Scheduler jobScheduler = scheduler;
            app = Javalin.create(config -> {
                config.jetty.port = port;
                config.jsonMapper(new JavalinJackson3().updateMapper(builder -> builder.configureForJackson2()));
                config.http.generateEtags = true;
                config.http.compressionStrategy = new CompressionStrategy(new Brotli(4), new Gzip(6));
                config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
                config.events.serverStopping(() -> {
                    cancellation.cancel();
                    jobScheduler.shutdown(true);
                });
                config.events.serverStopped(entityManagerUtil::shutdown);
                config.events.serverStopFailed(entityManagerUtil::shutdown);
                registerRoutes(config.routes, pathPrefix, responseCache);
            });
            BeaconFileController.deleteEmptyBeaconFiles();
            app.start();
            scheduler.start();
            return app;
        } catch (Exception startupFailure) {
            cancellation.cancel();
            try {
                if (app != null) {
                    app.stop();
                }
            } catch (Exception cleanupFailure) {
                startupFailure.addSuppressed(cleanupFailure);
            }
            try {
                if (scheduler != null && !scheduler.isShutdown()) {
                    scheduler.shutdown(true);
                }
            } catch (Exception cleanupFailure) {
                startupFailure.addSuppressed(cleanupFailure);
            }
            try {
                entityManagerUtil.shutdown();
            } catch (RuntimeException cleanupFailure) {
                startupFailure.addSuppressed(cleanupFailure);
            }
            throw startupFailure;
        }
    }

    private static void registerRoutes(RoutesConfig routes, String pathPrefix, ResponseCache responseCache) {
        // set UTF-8 as default charset
        routes.before(ctx -> ctx.res().setCharacterEncoding("UTF-8"));

        routes.get(pathPrefix + "/item/{id}", ctx -> {
            final long id;
            try {
                id = Long.parseLong(ctx.pathParam("id"));
            } catch (NumberFormatException e) {
                throw new BadRequestResponse("Keine gültige BEACON-ID");
            }
            if (id <= 0) {
                throw new BadRequestResponse("Keine gültige BEACON-ID");
            }
            final BeaconFile bfile = BeaconFileController.getBeaconFile(id);
            if (bfile == null) {
                throw new NotFoundResponse("Beacon-Datei " + id + " nicht gefunden");
            }
            ctx.contentType("text/plain; charset=utf-8").result(bfile.getBeaconFile());
        });

        routes.get(pathPrefix + "/item/{type}/{sector}/latest", ctx -> {

            final TYPE type = parseEnum(TYPE.class, ctx.pathParam("type"), "Kein gültiger Typ");
            final SECTOR sector = parseEnum(SECTOR.class, ctx.pathParam("sector"), "Keine gültige Kultursparte");

            final List<BeaconFile> bfileList = BeaconFileController.getBeaconFiles(type, sector, true);
            if (!bfileList.isEmpty()) {
                final BeaconFile bfile = bfileList.get(0);
                ctx.contentType("text/plain; charset=utf-8").result(bfile.getBeaconFile());
            } else {
                throw new NotFoundResponse("Keine Beacon-Datei gefunden");
            }
        });

        routes.get(pathPrefix, ctx -> {
            ctx.redirect(pathPrefix + "/list/latest?type=organisation&sector=all", HttpStatus.MOVED_PERMANENTLY);
        });

        routes.get(pathPrefix + "/list", ctx -> deliver(ctx, false, responseCache));
        routes.get(pathPrefix + "/list/latest", ctx -> deliver(ctx, true, responseCache));
    }

    private static void deliver(Context ctx, boolean latest, ResponseCache responseCache) {
        // HTML and JSON share the URL, and either response can be compressed.
        ctx.addHeader(Header.VARY, Header.ACCEPT);
        ctx.addHeader(Header.VARY, Header.ACCEPT_ENCODING);
        final var accepted = Collections.list(ctx.req().getHeaders(Header.ACCEPT));
        if (deliverHtml(accepted.isEmpty() ? null : String.join(",", accepted))) {
            responseCache.serveHtml(ctx);
            return;
        }

        final String requestedType = ctx.queryParam("type");
        final String requestedSector = ctx.queryParam("sector");
        final TYPE type = requestedType == null ? TYPE.ORGANISATION
                : parseEnum(TYPE.class, requestedType, "Kein gültiger Typ");
        final SECTOR sector = requestedSector == null ? SECTOR.ALL
                : parseEnum(SECTOR.class, requestedSector, "Keine gültige Kultursparte");

        responseCache.serveList(ctx, type, sector, latest);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String errorMessage) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(errorMessage);
        }
    }

    /**
     * Assigns each representation the quality of its most specific matching range,
     * then compares quality, specificity, and header order. A missing header selects
     * HTML, while an otherwise exact tie selects JSON for generic API clients.
     */
    private static boolean deliverHtml(String accept) {
        if (accept == null) {
            return true;
        }
        MediaPreference html = new MediaPreference(0, -1, Integer.MAX_VALUE);
        MediaPreference json = html;
        final String[] ranges = accept.split(",");
        for (int index = 0; index < ranges.length; index++) {
            final String[] parts = ranges[index].split(";", -1);
            final String mimeType = parts[0].strip().toLowerCase(Locale.ROOT);
            final double quality = qualityValue(parts);
            switch (mimeType) {
                case "text/html", "application/xhtml+xml" -> html = html.prefer(quality, 2, index);
                case "application/json", "text/javascript" -> json = json.prefer(quality, 2, index);
                case "text/*" -> html = html.prefer(quality, 1, index);
                case "application/*" -> json = json.prefer(quality, 1, index);
                case "*/*" -> {
                    html = html.prefer(quality, 0, index);
                    json = json.prefer(quality, 0, index);
                }
                default -> { }
            }
        }
        if (html.quality() == 0 && json.quality() == 0) {
            throw new NotAcceptableResponse("Keine unterstützte Darstellung angefordert");
        }
        return html.quality() > json.quality() || html.quality() == json.quality()
                && (html.specificity() > json.specificity()
                || html.specificity() == json.specificity() && html.order() < json.order());
    }

    private record MediaPreference(double quality, int specificity, int order) {
        private MediaPreference prefer(double candidateQuality, int candidateSpecificity, int candidateOrder) {
            return candidateSpecificity > specificity || candidateSpecificity == specificity && candidateQuality > quality
                    ? new MediaPreference(candidateQuality, candidateSpecificity, candidateOrder) : this;
        }
    }

    private static double qualityValue(String[] mediaRange) {
        for (int i = 1; i < mediaRange.length; i++) {
            final String[] parameter = mediaRange[i].split("=", 2);
            if (parameter.length == 2 && parameter[0].strip().equalsIgnoreCase("q")) {
                try {
                    final double quality = Double.parseDouble(parameter[1].strip());
                    return quality >= 0 && quality <= 1 ? quality : 0;
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 1;
    }
}
