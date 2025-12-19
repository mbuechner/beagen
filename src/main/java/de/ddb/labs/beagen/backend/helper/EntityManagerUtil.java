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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 *
 * @author Michael Büchner
 */
public class EntityManagerUtil {

    private static EntityManagerUtil instance;
    private final EntityManagerFactory EMF;

    private EntityManagerUtil() {
        EMF = Persistence.createEntityManagerFactory("BeagenFile");
    }

    public static synchronized EntityManagerUtil getInstance() {
        if (EntityManagerUtil.instance == null) {
            EntityManagerUtil.instance = new EntityManagerUtil();
        }
        return EntityManagerUtil.instance;
    }

    public EntityManager getEntityManager() {
        return EMF.createEntityManager();
    }

    public void shutdown() {
        if (EMF != null) {
            EMF.close();
        }
    }
}
