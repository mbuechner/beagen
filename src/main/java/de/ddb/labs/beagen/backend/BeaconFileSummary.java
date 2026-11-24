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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import de.ddb.labs.beagen.backend.data.SECTOR;
import de.ddb.labs.beagen.backend.data.TYPE;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Snapshot metadata returned by the JSON history API without the compressed data.
 *
 * @param type entity type covered by the snapshot
 * @param sector sector covered by the snapshot
 * @param created creation timestamp formatted as an ISO-8601 UTC instant
 * @param count number of data entries
 * @param id database identifier serialized as the download permalink
 */
@JsonPropertyOrder({"type", "sector", "created", "count", "@id"})
public record BeaconFileSummary(
        @JsonSerialize(using = TYPE.TypeSerializer.class) TYPE type,
        @JsonSerialize(using = SECTOR.SectorSerializer.class) SECTOR sector,
        String created,
        int count,
        @JsonProperty("@id") @JsonSerialize(using = BeaconFile.IdSerializer.class) Long id) {

    /**
     * Creates the summary from a database projection, truncating the creation time to seconds.
     *
     * @param type entity type covered by the snapshot
     * @param sector sector covered by the snapshot
     * @param created creation time read from the database
     * @param count number of data entries
     * @param id database identifier of the snapshot
     */
    public BeaconFileSummary(TYPE type, SECTOR sector, Date created, int count, Long id) {
        this(type, sector, created.toInstant().truncatedTo(ChronoUnit.SECONDS).toString(), count, id);
    }
}
