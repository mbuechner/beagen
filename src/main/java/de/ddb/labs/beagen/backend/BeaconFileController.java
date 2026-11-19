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
package de.ddb.labs.beagen.backend;

import de.ddb.labs.beagen.backend.data.SECTOR;
import de.ddb.labs.beagen.backend.data.TYPE;
import de.ddb.labs.beagen.backend.helper.EntityManagerUtil;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.persistence.TypedQuery;

/**
 * Database handler for Beacon files
 *
 * @author Michael Büchner
 */
@Slf4j
public class BeaconFileController {

    private static final AtomicLong listRevision = new AtomicLong();

    private BeaconFileController() {
    }

    public static void saveBeaconFile(BeaconFile beaconFile) {
        EntityManagerUtil.getInstance().runInTransaction(em -> em.persist(beaconFile));
        listRevision.incrementAndGet();
    }

    public static long getListRevision() {
        return listRevision.get();
    }

    /**
     * Get a Beacon file by its ID.
     *
     * @param id ID of Beacon File
     * @return Beacon file object
     */
    public static BeaconFile getBeaconFile(long id) {

        try {
            return EntityManagerUtil.getInstance().callInTransaction(em -> em.find(BeaconFile.class, id));
        } catch (Exception e) {
            log.error("Could not get Beacon file. {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get Beacon files by type (organsisation or person).
     *
     * @param type Type, can be null, which means all types
     * @param onlyLatest Provide only the latest Beacon files
     * @return List of Beacon file objects
     */
    public static List<BeaconFile> getBeaconFiles(TYPE type, boolean onlyLatest) {
        final Date lastDate = onlyLatest ? getLastDate(type) : null;

        String qs = "SELECT f FROM BeaconFile AS f ";

        if (onlyLatest && lastDate != null && type != null) {
            qs += "WHERE f.created = :lastDate AND f.type = :type ";
        } else if (onlyLatest && lastDate != null) {
            qs += "WHERE f.created = :lastDate ";
        } else if (type != null) {
            qs += "WHERE f.type = :type ";
        }

        final String query = qs + "ORDER BY f.created DESC, f.type, f.sector";

        try {
            return EntityManagerUtil.getInstance().callInTransaction(em -> {
                final TypedQuery<BeaconFile> q2 = em.createQuery(query, BeaconFile.class);

                if (lastDate != null) {
                    q2.setParameter("lastDate", lastDate);
                }

                if (type != null) {
                    q2.setParameter("type", type);
                }

                if (onlyLatest) {
                    q2.setMaxResults(1);
                }

                return q2.getResultList();
            });
        } catch (Exception e) {
            log.error("Could not get Beacon files. {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Get Beacon files by type (organsisation or person) and sector (Archive,
     * Musem etc.)
     *
     * @param type Type, can be null, which means all types
     * @param sector Sector, cannot be null.
     * @param onlyLatest Provide only the latest Beacon files
     * @return List of Beacon file objects
     */
    public static List<BeaconFile> getBeaconFiles(TYPE type, SECTOR sector, boolean onlyLatest) {
        try {
            return selectBeaconFiles("SELECT f", BeaconFile.class, type, sector, onlyLatest);
        } catch (Exception e) {
            log.error("Could not get Beacon files. {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public static List<BeaconFileSummary> getBeaconFileSummaries(TYPE type, SECTOR sector, boolean onlyLatest) {
        final String projection = "SELECT NEW de.ddb.labs.beagen.backend.BeaconFileSummary(f.type, f.sector, f.created, f.count, f.id)";
        return selectBeaconFiles(projection, BeaconFileSummary.class, type, sector, onlyLatest);
    }

    private static <T> List<T> selectBeaconFiles(String projection, Class<T> resultType, TYPE type, SECTOR sector, boolean onlyLatest) {
        final String query = projection + " FROM BeaconFile AS f WHERE "
                + (type == null ? "" : "f.type = :type AND ")
                + "f.sector = :sector ORDER BY f.created DESC, f.type, f.sector";

        return EntityManagerUtil.getInstance().callInTransaction(em -> {
            final TypedQuery<T> selection = em.createQuery(query, resultType);
            selection.setParameter("sector", sector);
            if (type != null) {
                selection.setParameter("type", type);
            }
            if (onlyLatest) {
                selection.setMaxResults(1);
            }
            return selection.getResultList();
        });
    }

    /**
     * Get last update date of Beacon files.
     *
     * @param type Type, can be null, which means all types
     * @return Date of last Beacon file update
     */
    public static Date getLastDate(TYPE type) {
        final String query = "SELECT MAX(f.created) FROM BeaconFile AS f"
                + (type == null ? "" : " WHERE f.type = :type");
        try {
            return EntityManagerUtil.getInstance().callInTransaction(em -> {
                final TypedQuery<Date> q2 = em.createQuery(query, Date.class);
                if (type != null) {
                    q2.setParameter("type", type);
                }
                return q2.getSingleResultOrNull();
            });
        } catch (Exception e) {
            log.warn("Could not get latest date for Beacon files. {}. Maybe there's no data yet?", e.getMessage());
            return null;
        }
    }
}
