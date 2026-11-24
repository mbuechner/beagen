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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores and queries BEACON snapshots in the application database.
 *
 * <p>History queries order snapshots by descending creation time, then by type and sector,
 * with descending IDs as the final tie-breaker. List views can use the metadata projection
 * to avoid loading compressed file contents.
 *
 * @author Michael Büchner
 */
public class BeaconFileController {

    private static final Logger log = LoggerFactory.getLogger(BeaconFileController.class);
    private static final AtomicLong listRevision = new AtomicLong();

    private BeaconFileController() {
    }

    /**
     * Persists a snapshot and advances the list revision after the transaction succeeds.
     *
     * @param beaconFile snapshot to persist
     * @throws IllegalArgumentException if the snapshot has no positive entry count
     */
    public static void saveBeaconFile(BeaconFile beaconFile) {
        saveBeaconFiles(List.of(beaconFile));
    }

    /**
     * Persists a batch of snapshots in a single transaction.
     * All entry counts are checked before the transaction starts.
     *
     * @param files snapshots to persist, or an empty list to leave the database untouched
     * @throws IllegalArgumentException if any snapshot has no positive entry count
     */
    public static void saveBeaconFiles(List<BeaconFile> files) {
        if (files.isEmpty()) {
            return;
        }
        for (BeaconFile file : files) {
            if (file.getCount() <= 0) {
                throw new IllegalArgumentException("A BEACON file must contain at least one entry.");
            }
        }
        EntityManagerUtil.getInstance().runInTransaction(em -> files.forEach(em::persist));
        listRevision.incrementAndGet();
    }

    /**
     * Removes legacy zero-entry snapshots during startup.
     * Cleanup failures are logged so the application can continue starting.
     */
    public static void deleteEmptyBeaconFiles() {
        try {
            final int deleted = EntityManagerUtil.getInstance().callInTransaction(em ->
                    em.createQuery("DELETE FROM BeaconFile f WHERE f.count = 0").executeUpdate());
            if (deleted > 0) {
                listRevision.incrementAndGet();
            }
            log.info("Startup cleanup removed {} empty BEACON files.", deleted);
        } catch (RuntimeException e) {
            log.warn("Could not complete the startup cleanup of empty BEACON files.", e);
        }
    }

    /**
     * Returns the in-process revision used to invalidate cached list responses.
     * A successful nonempty save or deletion advances the revision.
     *
     * @return the current list revision
     */
    public static long getListRevision() {
        return listRevision.get();
    }

    /**
     * Loads a snapshot for a download by its database identifier.
     *
     * @param id database identifier of the snapshot
     * @return the matching snapshot or {@code null} if it is absent
     */
    public static BeaconFile getBeaconFile(long id) {
        return EntityManagerUtil.getInstance().callInTransaction(em -> em.find(BeaconFile.class, id));
    }

    /**
     * Lists snapshots across sectors, optionally limited to one entity type.
     * Query failures are logged and produce an empty result.
     *
     * @param type entity type or {@code null} to include every type
     * @param onlyLatest whether to return at most the first snapshot in history order
     * @return the matching snapshots in history order
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

        final String query = qs + "ORDER BY f.created DESC, f.type, f.sector, f.id DESC";

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
     * Lists snapshots for one sector with an optional entity-type filter.
     * {@link SECTOR#ALL ALL} selects the aggregate snapshots.
     *
     * @param type entity type or {@code null} to include every type
     * @param sector required sector of the snapshots
     * @param onlyLatest whether to return at most the first snapshot in history order
     * @return the matching snapshots in history order
     */
    public static List<BeaconFile> getBeaconFiles(TYPE type, SECTOR sector, boolean onlyLatest) {
        return selectBeaconFiles("SELECT f", BeaconFile.class, type, sector, onlyLatest);
    }

    /**
     * Lists snapshot metadata without selecting the compressed payloads.
     * The filters and ordering match {@link #getBeaconFiles(TYPE, SECTOR, boolean)}.
     *
     * @param type entity type or {@code null} to include every type
     * @param sector required sector of the snapshots
     * @param onlyLatest whether to return at most the first summary in history order
     * @return metadata for the matching snapshots in history order
     */
    public static List<BeaconFileSummary> getBeaconFileSummaries(TYPE type, SECTOR sector, boolean onlyLatest) {
        final String projection = "SELECT NEW de.ddb.labs.beagen.backend.BeaconFileSummary(f.type, f.sector, f.created, f.count, f.id)";
        return selectBeaconFiles(projection, BeaconFileSummary.class, type, sector, onlyLatest);
    }

    private static <T> List<T> selectBeaconFiles(String projection, Class<T> resultType, TYPE type, SECTOR sector, boolean onlyLatest) {
        final String query = projection + " FROM BeaconFile AS f WHERE "
                + (type == null ? "" : "f.type = :type AND ")
                + "f.sector = :sector ORDER BY f.created DESC, f.type, f.sector, f.id DESC";

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
     * Finds the most recent snapshot creation time across sectors.
     *
     * @param type entity type or {@code null} to include every type
     * @return the latest creation time, or {@code null} if no time is available or the query fails
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
