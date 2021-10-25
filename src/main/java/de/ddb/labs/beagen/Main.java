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
package de.ddb.labs.beagen;

import de.ddb.labs.beagen.backend.BeaconFile;
import de.ddb.labs.beagen.backend.BeaconFileController;
import de.ddb.labs.beagen.backend.helper.Configuration;
import de.ddb.labs.beagen.backend.helper.EntityManagerUtil;
import de.ddb.labs.beagen.backend.jobs.BeaconJob;
import de.ddb.labs.beagen.backend.data.SECTOR;
import de.ddb.labs.beagen.backend.data.TYPE;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import java.util.List;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

/**
 *
 * @author Michael Büchner
 */
public class Main {

    // Configuration
    private static final String BEAGEN_DATABASE_DIR = "beagen.database.dir";
    private static final String BEAGEN_BASEURL = "beagen.baseurl";
    private static final String BEAGEN_PORT = "beagen.port";
    private static final String BEAGEN_PATHPREFIX = "beagen.pathprefix";
    private static final String BEAGEN_CRON = "beagen.cron";
    // Job Scheduler
    private static Scheduler quartzScheduler;

    /**
     * Main entry point which starts a local http server listening on port 80.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        // set System properties for pathes
        // get env and overwrite default configuration
        if (System.getenv(BEAGEN_DATABASE_DIR) != null) {
            System.setProperty(BEAGEN_DATABASE_DIR, System.getenv(BEAGEN_DATABASE_DIR));
            Configuration.get().setValue(BEAGEN_DATABASE_DIR, System.getenv(BEAGEN_DATABASE_DIR));
        } else {
            System.setProperty(BEAGEN_DATABASE_DIR, Configuration.get().getValue(BEAGEN_DATABASE_DIR));
        }
        if (System.getenv(BEAGEN_BASEURL) != null) {
            Configuration.get().setValue(BEAGEN_BASEURL, System.getenv(BEAGEN_BASEURL));
        }
        if (System.getenv(BEAGEN_PATHPREFIX) != null) {
            Configuration.get().setValue(BEAGEN_PATHPREFIX, System.getenv(BEAGEN_PATHPREFIX));
        }
        if (System.getenv(BEAGEN_PORT) != null) {
            Configuration.get().setValue(BEAGEN_PORT, System.getenv(BEAGEN_PORT));
        }
        if (System.getenv(BEAGEN_CRON) != null) {
            Configuration.get().setValue(BEAGEN_CRON, System.getenv(BEAGEN_CRON));
        }

        System.out.println(BEAGEN_DATABASE_DIR + "=" + Configuration.get().getValue(BEAGEN_DATABASE_DIR));
        System.out.println(BEAGEN_BASEURL + "=" + Configuration.get().getValue(BEAGEN_BASEURL));
        System.out.println(BEAGEN_PATHPREFIX + "=" + Configuration.get().getValue(BEAGEN_PATHPREFIX));
        System.out.println(BEAGEN_PORT + "=" + Configuration.get().getValue(BEAGEN_PORT));
        System.out.println(BEAGEN_CRON + "=" + Configuration.get().getValue(BEAGEN_CRON));

        // start update job
        final JobDetail job = JobBuilder.newJob(BeaconJob.class)
                .withIdentity("cronjob", "crongroup")
                .build();

        final Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("crontrigger", "crongroup")
                .startNow()
                .withSchedule(CronScheduleBuilder.cronSchedule(Configuration.get().getValue(BEAGEN_CRON)))
                .build();

        quartzScheduler = new StdSchedulerFactory().getDefaultScheduler();
        quartzScheduler.start();
        quartzScheduler.scheduleJob(job, trigger);
        

        // start javalin server
        final Javalin app = Javalin.create(config -> {
            config.autogenerateEtags = true;
            config.enableCorsForAllOrigins();

        }).events(event -> {
            // init DB
            event.serverStarting(() -> EntityManagerUtil.getInstance());
            // close DB connection
            event.serverStopped(() -> EntityManagerUtil.getInstance().shutdown());
        }).start(Integer.parseInt(Configuration.get().getValue(BEAGEN_PORT)));

        // set UTF-8 as default charset
        app.before(ctx -> ctx.res.setCharacterEncoding("UTF-8"));

        app.get(Configuration.get().getValue(BEAGEN_PATHPREFIX) + "/item/{id}", ctx -> {
            final Long id = Long.parseLong(ctx.pathParam("id"));
            final BeaconFile bfile = BeaconFileController.getBeaconFile(id);
            if (bfile == null) {
                throw new NotFoundResponse("Beacon-Datei " + id + " nicht gefunden");
            }
            ctx.result(bfile.getBeaconFile());
        });

        app.get(Configuration.get().getValue(BEAGEN_PATHPREFIX) + "/item/{type}/{sector}/latest", ctx -> {

            TYPE type;
            try {
                type = TYPE.valueOf(ctx.pathParam("type").toUpperCase());
            } catch (Exception e) {
                throw new BadRequestResponse("Kein gültiger Typ");
            }

            SECTOR sector;
            try {
                sector = SECTOR.valueOf(ctx.pathParam("sector").toUpperCase());
            } catch (Exception e) {
                throw new BadRequestResponse("Keine gültige Kultursparte");
            }

            final List<BeaconFile> bfileList = BeaconFileController.getBeaconFiles(type, sector, true);
            if (bfileList.size() > 0) {
                final BeaconFile bfile = bfileList.get(0);
                ctx.result(bfile.getBeaconFile());
            } else {
                throw new NotFoundResponse("Keine Beacon-Datei gefunden");
            }
        });

        app.get(Configuration.get().getValue(BEAGEN_PATHPREFIX), ctx -> {
            ctx.redirect(Configuration.get().getValue(BEAGEN_PATHPREFIX) + "/list/latest?type=organisation&sector=all", 301);
        });

        app.get(Configuration.get().getValue(BEAGEN_PATHPREFIX) + "/list", ctx -> deliver(ctx, false));

        // set paths
        app.get(Configuration.get().getValue(BEAGEN_PATHPREFIX) + "/list/latest", ctx -> deliver(ctx, true));
    }

    private static void deliver(Context ctx, boolean latest) {
        if (deliverHtml(ctx.req.getHeader("Accept"))) {
            ctx.render("/base.mustache");
            return;
        }

        TYPE type = null;
        try {
            final String t = ctx.queryParam("type") == null ? "organisation" : ctx.queryParam("type");
            if (t == null) {
                throw new IllegalArgumentException();
            }
            type = TYPE.valueOf(t.toUpperCase());
        } catch (Exception e) {
            throw new BadRequestResponse("Kein gültiger Typ");
        }

        SECTOR sector = null;
        try {
            final String s = ctx.queryParam("sector") == null ? "all" : ctx.queryParam("sector");
            if (s == null) {
                throw new IllegalArgumentException();
            }
            sector = SECTOR.valueOf(s.toUpperCase());
        } catch (Exception e) {
            throw new BadRequestResponse("Keine gültige Kultursparte");
        }

        ctx.json(BeaconFileController.getBeaconFiles(type, sector, latest));
    }

    private static boolean deliverHtml(String accept) {
        if (accept == null) {
            return true;
        }
        accept = accept.substring(0, accept.indexOf(';') == -1 ? accept.length() : accept.indexOf(';'));
        final String[] mimeTypes = accept.split(",");

        for (int i = 0; i < mimeTypes.length; ++i) {
            if (mimeTypes[i].trim().equalsIgnoreCase("text/html")) {
                return true;
            }
            if (mimeTypes[i].trim().equalsIgnoreCase("application/xhtml+xml")) {
                return true;
            }
            if (mimeTypes[i].trim().equalsIgnoreCase("application/json")) {
                return false;
            }
            if (mimeTypes[i].trim().equalsIgnoreCase("text/javascript")) {
                return false;
            }
        }

        return false;
    }
}
