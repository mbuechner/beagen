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
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.resource.PathResource;
import static org.quartz.JobBuilder.newJob;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import org.quartz.Trigger;
import static org.quartz.TriggerBuilder.newTrigger;
import org.quartz.impl.StdSchedulerFactory;

public class Main {

    public static void startServer() throws Exception {
        {
            final Thread t1 = new Thread() {
                @Override
                public void run() {

                    final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                    context.setContextPath("/");

                    final Server server = new Server();
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

                        final Path webRootPath = new File("static/beacons").toPath().toRealPath();
                        context.setBaseResource(new PathResource(webRootPath));
                        context.setWelcomeFiles(new String[]{"index.html"});

                        // SERVLETS ***
                        // context.addServlet(MyServlet.class, "/mypath/");
                        context.addServlet(DefaultServlet.class, "/*"); // always last, always on "/"
                        // ^ SERVLETS ***

                        server.start();
                        server.join();

                    } catch (Exception e) {
                        System.err.println("Error starting uiserver: " + e);
                    } finally {
                        server.destroy();
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
                                .withSchedule(simpleSchedule()
                                        .withIntervalInSeconds(5)
                                        .repeatForever())
                                .build();

                        Scheduler scheduler = new StdSchedulerFactory().getScheduler();
                        scheduler.start();
                        scheduler.scheduleJob(job, trigger);

                    } catch (SchedulerException ex) {
                        // LOG
                    }
                }
            };

            t1.start();
            t2.start();
        }
    }

    public static void main(String[] args) throws Exception {
        startServer();
    }
}
