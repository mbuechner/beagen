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
package de.ddb.labs.beagen;

import de.ddb.labs.beagen.helper.Configuration;
import de.ddb.labs.beagen.servlets.GetBeaconFileServlet;
import de.ddb.labs.beagen.servlets.ListOlderBeaconServlet;
import de.ddb.labs.beagen.servlets.ListNewestBeaconServlet;
import de.ddb.labs.beagen.servlets.ListServlet;
import de.ddb.labs.beagen.helper.EntityManagerUtil;
import de.ddb.labs.beagen.jobs.BeaconJob;
import java.io.File;
import java.nio.file.Path;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.quartz.CronScheduleBuilder;
import static org.quartz.JobBuilder.newJob;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import static org.quartz.TriggerBuilder.newTrigger;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeagenServer {

    // Jetty Server
    private static Server server;

    // Job Scheduler
    private static Scheduler scheduler;

    // Logger
    private static final Logger LOG = LoggerFactory.getLogger(BeagenServer.class);

    private static void startServer() throws Exception {
        {
            final Thread t1 = new Thread() {
                @Override
                public void run() {

                    final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                    context.setContextPath("/");

                    server = new Server();
                    final ServerConnector connector = new ServerConnector(server);
                    connector.setPort(80);
                    server.addConnector(connector);
                    server.setHandler(context);

                    // HANDLERS ***
                    final GzipHandler gzip = new GzipHandler();
                    gzip.setIncludedMethods("GET", "POST");
                    gzip.setMinGzipSize(245);
                    gzip.setIncludedMimeTypes("text/plain", "text/css", "text/html", "application/javascript");
                    server.setHandler(gzip);
                    gzip.setHandler(context);
                    // ^ HANDLERS ***

                    // FILTERS ***
                    final FilterHolder filterHolder = new FilterHolder();

                    filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT,POST,DELETE,OPTIONS");
                    filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
                    filterHolder.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
                    filterHolder.setInitParameter("allowedHeaders", "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin");
                    filterHolder.setInitParameter("allowCredentials", "true");

                    filterHolder.setFilter(new CrossOriginFilter());
                    context.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
                    // ^ FILTERS ***

                    try {

                        // final Path webRootPath = new File("{logs.dir}").toPath().toRealPath();
                        // context.setBaseResource(new PathResource(webRootPath));
                        // add special pathspec for the Logs
                        final Path path = new File(Configuration.getInstance().getValue("beagen.log.dir")).toPath().toRealPath();
                        final ServletHolder holder = new ServletHolder("staticLogs", DefaultServlet.class);
                        holder.setInitParameter("resourceBase", path.toUri().toASCIIString());
                        holder.setInitParameter("dirAllowed", "true");
                        holder.setInitParameter("pathInfoOnly", "true");

                        context.setWelcomeFiles(new String[]{"index.html"});

                        // SERVLETS ***
                        context.addServlet(holder, "/logs/*");
                        context.addServlet(ListServlet.class, "/list/*");
                        context.addServlet(ListOlderBeaconServlet.class, "/listAll/*");
                        context.addServlet(GetBeaconFileServlet.class, "/item/*");
                        context.addServlet(ListNewestBeaconServlet.class, "/*"); // always last, always on "/"
                        // ^ SERVLETS ***

                        server.start();
                        server.join();

                    } catch (Exception e) {
                        LOG.error("Error while starting Jetty server.", e);
                    }
                }
            };

            final Thread t2 = new Thread() {
                @Override
                public void run() {
                    try {

                        final JobDetail job = newJob(BeaconJob.class)
                                .withIdentity("job1", "group1")
                                .build();

                        final Trigger trigger = newTrigger()
                                .withIdentity("trigger1", "group1")
                                .startNow()
                                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/30 * * * ?"))
                                .build();

                        scheduler = new StdSchedulerFactory().getScheduler();
                        scheduler.start();
                        scheduler.scheduleJob(job, trigger);

                    } catch (SchedulerException ex) {
                        LOG.error("Could NOT start job scheduler!", ex);
                    }
                }
            };

            // start Jetty server
            t1.start();
            // start Job scheduler
            t2.start();
            // open db connection
            EntityManagerUtil.getInstance();
        }
    }

    public void start() throws Exception {
        
        // we need to handle a shutdown by Docker
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    LOG.info("Shutting server gracefully down...");
                    if (server != null) {
                        server.stop();
                        server.destroy();
                    }
                } catch (Exception ex) {
                    LOG.error("Could NOT shutdown server!", ex);
                }
            }
        });

        // we need to handle a shutdown by Docker
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    LOG.info("Shutting job scheduler gracefully down...");
                    if (scheduler != null) {
                        scheduler.shutdown();
                    }
                } catch (SchedulerException ex) {
                    LOG.error("Could NOT shutdown job scheduler!", ex);
                }
            }
        });

        // we need to handle a shutdown by Docker
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                LOG.info("Shutting database connection gracefully down...");
                EntityManagerUtil.getInstance().shutdown();
            }
        });

        // start server + job scheduler + DB connection
        startServer();
    }
}
