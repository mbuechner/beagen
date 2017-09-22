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
package de.ddb.labs.beagen.jobs;

import de.ddb.labs.beagen.beacon.BeaconFileMaker;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeaconJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(BeaconJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        LOG.info("Start BEACON maker job...");
        BeaconFileMaker.getInstance().run();
        LOG.info("BEACON maker job finished.");
    }
}
