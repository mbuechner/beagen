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
        if (System.getenv("beagen.log.dir") != null) {
            System.setProperty("beagen.log.dir", System.getenv("beagen.log.dir"));
            Configuration.get().setValue("beagen.log.dir", System.getenv("beagen.log.dir"));
        } else {
            System.setProperty("beagen.log.dir", Configuration.get().getValue("beagen.log.dir"));
        }
        if (System.getenv("beagen.database.dir") != null) {
            System.setProperty("beagen.database.dir", System.getenv("beagen.database.dir"));
            Configuration.get().setValue("beagen.database.dir", System.getenv("beagen.database.dir"));
        } else {
            System.setProperty("beagen.database.dir", Configuration.get().getValue("beagen.database.dir"));
        }
        if (System.getenv("beagen.baseurl") != null) {
            Configuration.get().setValue("beagen.baseurl", System.getenv("beagen.baseurl"));
        }
        if (System.getenv("beagen.cron") != null) {
            Configuration.get().setValue("beagen.cron", System.getenv("beagen.cron"));
        }
        if (System.getenv("beagen.ddbapikey") != null) {
            Configuration.get().setValue("beagen.ddbapikey", System.getenv("beagen.ddbapikey"));
        }

        System.out.println("beagen.log.dir=" + Configuration.get().getValue("beagen.log.dir"));
        System.out.println("beagen.database.dir=" + Configuration.get().getValue("beagen.database.dir"));
        System.out.println("beagen.baseurl=" + Configuration.get().getValue("beagen.baseurl"));
        System.out.println("beagen.cron=" + Configuration.get().getValue("beagen.cron"));
        System.out.println("beagen.ddbapikey=" + Configuration.get().getValue("beagen.ddbapikey"));

        // start update job
        final JobDetail job = JobBuilder.newJob(BeaconJob.class)
                .withIdentity("cronjob", "crongroup")
                .build();

        final Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("crontrigger", "crongroup")
                .startNow()
                .withSchedule(CronScheduleBuilder.cronSchedule(Configuration.get().getValue("beagen.cron")))
                .build();

        quartzScheduler = new StdSchedulerFactory().getScheduler();
        quartzScheduler.start();
        quartzScheduler.scheduleJob(job, trigger);

        // start javalin server
        final Javalin app = Javalin.create(config -> {
            config.autogenerateEtags = true;
            config.enableCorsForAllOrigins();

        }).events(event -> {
            event.serverStarting(() -> {
                EntityManagerUtil.getInstance(); // init DB
            });
            event.serverStopped(() -> {
                EntityManagerUtil.getInstance().shutdown(); // close DB connection
            });
        }).start(80);

        // set UTF-8 as default charset
        app.before(ctx -> {
            ctx.res.setCharacterEncoding("UTF-8");
        });

        app.get("/item/:id", ctx -> {
            final Long id = Long.parseLong(ctx.pathParam("id"));
            final BeaconFile bfile = BeaconFileController.getBeaconFile(id);
            if (bfile == null) {
                throw new NotFoundResponse("Beacon-Datei " + id + " nicht gefunden");
            }
            ctx.result(bfile.getBeaconFile());
        });

        app.get("/item/:type/:sector/latest", ctx -> {

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
                //ctx.contentType("text/plain");
                ctx.result(bfile.getBeaconFile());
            } else {
                throw new NotFoundResponse("Keine Beacon-Datei gefunden");
            }
        });

        app.get("/", ctx -> {
            final String prefix = ctx.req.getHeader("X-Forwarded-Prefix");
            ctx.redirect(((prefix != null && !prefix.isEmpty()) ? prefix : "") + "/list/latest?type=organisation&sector=all", 301);
        });

        app.get("/list", ctx -> {
            deliver(ctx, false);
        });

        // set paths
        app.get("/list/latest", ctx -> {
            deliver(ctx, true);
        });
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
