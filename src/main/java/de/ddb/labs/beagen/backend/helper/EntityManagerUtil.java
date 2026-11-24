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
package de.ddb.labs.beagen.backend.helper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the application's persistence factory and transaction boundaries.
 * Startup must initialize the factory before HTTP handlers or import jobs access it.
 *
 * @author Michael Büchner
 */
public final class EntityManagerUtil {

    private static final Logger log = LoggerFactory.getLogger(EntityManagerUtil.class);
    private static final Object INSTANCE_LOCK = new Object();
    private static EntityManagerUtil instance;
    private final EntityManagerFactory factory;

    private EntityManagerUtil(EntityManagerFactory factory) {
        this.factory = factory;
    }

    private static int normalizeLegacyEnums(EntityManager em) {
        // Hibernate's CHAR-to-VARCHAR schema update preserves trailing spaces in existing values.
        final String hasPadding = "type <> RTRIM(type) OR sector <> RTRIM(sector)";
        if (em.createNativeQuery("SELECT 1 FROM BeaconFile WHERE " + hasPadding)
                .setMaxResults(1).getResultList().isEmpty()) {
            return 0;
        }
        return em.createNativeQuery("UPDATE BeaconFile SET type = RTRIM(type), sector = RTRIM(sector) WHERE " + hasPadding)
                .executeUpdate();
    }

    /**
     * Opens the persistence unit and removes trailing spaces from legacy type and
     * sector values before making the database available.
     *
     * @return the initialized persistence manager for this application run
     * @throws IllegalStateException if the database is already running
     * @throws jakarta.persistence.PersistenceException if persistence setup fails
     */
    public static EntityManagerUtil initialize() {
        synchronized (INSTANCE_LOCK) {
            if (instance != null) {
                throw new IllegalStateException("Database is already running");
            }
            final EntityManagerFactory factory = Persistence.createEntityManagerFactory("BeagenFile");
            try {
                final int normalized = factory.callInTransaction(EntityManagerUtil::normalizeLegacyEnums);
                if (normalized > 0) {
                    log.info("Removed legacy enum padding from {} BEACON files.", normalized);
                }
            } catch (RuntimeException e) {
                try {
                    factory.close();
                } catch (RuntimeException closeFailure) {
                    e.addSuppressed(closeFailure);
                }
                throw e;
            }
            instance = new EntityManagerUtil(factory);
            return instance;
        }
    }

    /**
     * Retrieves the persistence manager for the running application.
     *
     * @return the manager created by {@link #initialize()}
     * @throws IllegalStateException if the database has not started or has shut down
     */
    public static EntityManagerUtil getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (instance == null) {
                throw new IllegalStateException("Database is not running");
            }
            return instance;
        }
    }

    /**
     * Executes an operation with an entity manager scoped to a transaction.
     *
     * @param <T> the result type
     * @param action the operation to run
     * @return the result of the operation
     */
    public <T> T callInTransaction(Function<EntityManager, T> action) {
        return factory.callInTransaction(action);
    }

    /**
     * Executes an operation that does not return a result in a transaction.
     *
     * @param action the operation to run with the transaction's entity manager
     */
    public void runInTransaction(Consumer<EntityManager> action) {
        factory.runInTransaction(action);
    }

    /**
     * Releases the persistence factory once the application has stopped using it.
     * Repeated calls on the same instance have no effect.
     */
    public void shutdown() {
        synchronized (INSTANCE_LOCK) {
            if (instance == this) {
                instance = null;
                factory.close();
            }
        }
    }
}
