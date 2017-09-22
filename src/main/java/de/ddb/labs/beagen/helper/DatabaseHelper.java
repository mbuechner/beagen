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
package de.ddb.labs.beagen.helper;

import de.ddb.labs.beagen.beacon.BeaconFile;
import de.ddb.labs.beagen.beacon.BeaconFile.SECTOR;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;

/**
 *
 * @author Michael Büchner
 */
public class DatabaseHelper {

    public static BeaconFile getBeaconFile(long id) {

        final EntityManager em = EntityManagerUtil.getInstance().getEntityManager();
        final EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            final Query q2 = em.createQuery("SELECT f FROM BeaconFile AS f WHERE f.id = :myid", BeaconFile.class);
            q2.setParameter("myid", id);
            q2.setMaxResults(1);
            return (BeaconFile) q2.getResultList().get(0);
        } catch (Exception e) {
            return null;
        } finally {
            tx.commit();
        }
    }

    public static List<BeaconFile> getLastBeaconFile() {
        final EntityManager em = EntityManagerUtil.getInstance().getEntityManager();
        final EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            final Query q2 = em.createQuery("SELECT f FROM BeaconFile AS f ORDER BY f.created DESC, f.type", BeaconFile.class);
            q2.setMaxResults(SECTOR.values().length);
            final List<BeaconFile> result = q2.getResultList();
            return result;
        } catch (Exception e) {
            return null;
        } finally {
            tx.commit();
        }
    }

    public static BeaconFile getLastBeaconFile(SECTOR sector) {
        final EntityManager em = EntityManagerUtil.getInstance().getEntityManager();
        final EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            final Query q2 = em.createQuery("SELECT f FROM BeaconFile AS f WHERE f.type = :mysector ORDER BY f.created DESC", BeaconFile.class);
            q2.setParameter("mysector", sector);
            q2.setMaxResults(1);
            return (BeaconFile) q2.getResultList().get(0);
        } catch (Exception e) {
            return null;
        } finally {
            tx.commit();
        }

    }
}
