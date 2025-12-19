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
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;

/**
 * Database handler for Beacon files
 *
 * @author Michael Büchner
 */
@Slf4j
public class BeaconFileController {

    private BeaconFileController() {
    }

    /**
     * Get a Beacon file by its ID.
     *
     * @param id ID of Beacon File
     * @return Beacon file object
     */
    public static BeaconFile getBeaconFile(long id) {

        final EntityManager em = EntityManagerUtil.getInstance().getEntityManager();
        final EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            final TypedQuery<BeaconFile> q2 = em.createQuery("SELECT f FROM BeaconFile AS f WHERE f.id = :myid", BeaconFile.class);
            q2.setParameter("myid", id);
            q2.setMaxResults(1);
            if (!q2.getResultList().isEmpty()) {
                return q2.getResultList().get(0);
            } else {
                return null;
            }
        } catch (Exception e) {
            log.error("Could not get Beacon file. {}", e.getMessage(), e);
            return null;
        } finally {
            tx.commit();
            em.close();
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
        Date lastDate = null;
        if (onlyLatest) {
            lastDate = getLastDate(type);
        }

        String qs = "SELECT f FROM BeaconFile AS f ";

        if (onlyLatest && lastDate != null && type != null) {
            qs += "WHERE f.created = :lastDate AND f.type = :type ";
        } else if (onlyLatest && lastDate != null) {
            qs += "WHERE f.created = :lastDate ";
        } else if (type != null) {
            qs += "WHERE f.type = :type ";
        }

        qs += "ORDER BY f.created DESC, f.type, f.sector ";

        final EntityManager em = EntityManagerUtil.getInstance().getEntityManager();
        final EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            final TypedQuery<BeaconFile> q2 = em.createQuery(qs, BeaconFile.class);

            if (onlyLatest && lastDate != null) {
                q2.setParameter("lastDate", lastDate);
            }

            if (type != null) {
                q2.setParameter("type", type);
            }

            if (onlyLatest) {
                q2.setMaxResults(1);
            }

            final List<BeaconFile> result = q2.getResultList();
            return result;
        } catch (Exception e) {
            log.error("Could not get Beacon files. {}", e.getMessage(), e);
            return new ArrayList<>();
        } finally {
            tx.commit();
            em.close();
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

        String qs = "SELECT f FROM BeaconFile AS f WHERE ";

        if (type != null) {
            qs += "f.type = :type AND ";
        }

        qs += "f.sector = :mysector ORDER BY f.created DESC, f.type, f.sector ";

        final EntityManager em = EntityManagerUtil.getInstance().getEntityManager();
        final EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            final TypedQuery<BeaconFile> q2 = em.createQuery(qs, BeaconFile.class);
            q2.setParameter("mysector", sector);
            if (type != null) {
                q2.setParameter("type", type);
            }

            if (onlyLatest) {
                q2.setMaxResults(1);
            }
            final List<BeaconFile> result = q2.getResultList();
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        } finally {
            tx.commit();
            em.close();
        }

    }

    /**
     * Get last update date of Beacon files.
     *
     * @param type Type, can be null, which means all types
     * @return Date of last Beacon file update
     */
    public static Date getLastDate(TYPE type) {
        String qs = "SELECT MAX(f.created) FROM BeaconFile AS f ";

        if (type != null) {
            qs += "WHERE f.type = :type ";
        }

        final EntityManager em = EntityManagerUtil.getInstance().getEntityManager();
        final EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            final TypedQuery<Date> q2 = em.createQuery(qs, Date.class);
            if (type != null) {
                q2.setParameter("type", type);
            }
            final List<Date> result = q2.getResultList();
            return result.isEmpty() ? null : result.get(0);
        } catch (Exception e) {
            log.warn("Could not get latest date for Beacon files. {}. Maybe there's no data yet?", e.getMessage());
            return null;
        } finally {
            tx.commit();
            em.close();
        }
    }
}
