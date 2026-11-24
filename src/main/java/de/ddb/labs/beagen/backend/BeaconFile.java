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
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import de.ddb.labs.beagen.backend.data.SECTOR.SectorSerializer;
import de.ddb.labs.beagen.backend.data.TYPE.TypeSerializer;
import de.ddb.labs.beagen.backend.helper.Configuration;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import jakarta.persistence.Basic;
import jakarta.persistence.FetchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A stored BEACON snapshot for one entity type and sector.
 *
 * <p>The database stores the gzip-compressed data lines. The download adds a UTF-8 header
 * from the configured template, including the timestamp, permalink, and latest-feed URL.
 * Persisted snapshots are identified by their database IDs. Use {@link #hasSameData(BeaconFile)}
 * to check whether a new snapshot contains the same data as an earlier one.
 *
 * @author Michael Büchner
 */
@Entity
@Table(name = "BeaconFile", indexes = {
    @Index(name = "idx_beacon_created", columnList = "created"),
    @Index(name = "idx_beacon_type", columnList = "type"),
    @Index(name = "idx_beacon_sector", columnList = "sector"),
    @Index(name = "idx_beacon_type_sector_created", columnList = "type,sector,created")
})
public class BeaconFile implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String BEAGEN_BASEURL = "beagen.baseurl";
    private static final String API_ITEM_METHODE = "/item";

    /** Database-generated snapshot identifier. */
    @Id
    @Basic(optional = false)
    //@NotNull
    // Keep allocating IDs from the Hibernate 5 sequence in existing databases.
    @SequenceGenerator(name = "beacon_file_id", sequenceName = "hibernate_sequence", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "beacon_file_id")
    @Column(name = "id", updatable = false, nullable = false)
    @JsonSerialize(using = IdSerializer.class)
    @JsonProperty("@id")
    private Long id;

    /** Entity type covered by this snapshot. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Basic(optional = false)
    //@Size(min = 1, max = 32)
    @Column(name = "type")
    @JsonSerialize(using = TypeSerializer.class)
    private TYPE type;

    /** Sector covered by this snapshot. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Basic(optional = false)
    //@Size(min = 1, max = 32)
    @Column(name = "sector")
    @JsonSerialize(using = SectorSerializer.class)
    private SECTOR sector;

    /** Time at which the snapshot was created. */
    @Basic(optional = false)
    @Column(name = "created")
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private Date created;

    /** Number of data entries in the snapshot. */
    @Basic(optional = false)
    @Column(name = "count")
    private int count;

    /** Gzip-compressed UTF-8 data lines. The download header is generated separately. */
    @Basic(optional = false, fetch = FetchType.LAZY)
    @Lob
    @Column(name = "content")
    @JsonIgnore
    private byte[] content;

    /**
     * Creates an empty entity for the persistence provider to populate.
     */
    public BeaconFile() {
    }

    /**
     * Creates a snapshot with the supplied metadata and gzip-compressed data.
     *
     * @param type entity type covered by the snapshot
     * @param sector sector covered by the snapshot
     * @param created creation time, which is copied
     * @param count number of data entries, positive for snapshots to be saved
     * @param content gzip-compressed UTF-8 data lines, copied on construction
     */
    public BeaconFile(TYPE type, SECTOR sector, Date created, int count, byte[] content) {
        this.type = type;
        this.sector = sector;
        this.created = new Date(created.getTime());
        this.count = count;
        this.content = Arrays.copyOf(content, content.length);
    }

    /**
     * Returns the stored snapshot identifier.
     *
     * @return the identifier or {@code null} if it has yet to be assigned
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the entity identifier in memory. The persisted identifier is immutable.
     *
     * @param id snapshot identifier
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the sector represented by the data in this snapshot.
     *
     * @return the snapshot's sector
     */
    public SECTOR getSector() {
        return sector;
    }

    /**
     * Sets the sector used to select the download header and classify the snapshot.
     *
     * @param sector snapshot sector
     */
    public void setSector(SECTOR sector) {
        this.sector = sector;
    }

    /**
     * Returns the creation time used to order snapshots.
     *
     * @return a copy of the snapshot's creation time
     */
    public Date getCreated() {
        return new Date(created.getTime());
    }

    /**
     * Sets the creation time from a defensive copy.
     *
     * @param created snapshot creation time
     */
    public void setCreated(Date created) {
        this.created = new Date(created.getTime());
    }

    /**
     * Builds the full download by prepending the configured header to the data lines.
     * The gzip payload is read to completion before the stream is returned.
     *
     * @return a stream of the complete UTF-8 BEACON file for the caller to close
     * @throws IOException if the header configuration cannot be loaded or the payload cannot be decoded
     */
    @JsonIgnore
    public InputStream getBeaconFile() throws IOException {
        final DownloadBuffer download = new DownloadBuffer();
        download.writeBytes(getBeaconHeader().getBytes(StandardCharsets.UTF_8));
        // Validate the complete gzip member before handing the response to the HTTP server.
        try (InputStream stream = openContent()) {
            stream.transferTo(download);
        }
        return download.asInputStream();
    }

    private InputStream openContent() throws IOException {
        return new GZIPInputStream(new ByteArrayInputStream(content));
    }

    private static final class DownloadBuffer extends ByteArrayOutputStream {
        private InputStream asInputStream() {
            return new ByteArrayInputStream(buf, 0, count);
        }
    }

    private String getBeaconHeader() throws IOException {

        final String dt = getCreated().toInstant().truncatedTo(ChronoUnit.SECONDS).toString();

        final StringBuilder sb = new StringBuilder();

        final String headerKey = "beagen.beacon.header." + type.getName().toLowerCase(Locale.ROOT) + "." + sector.getShortName();
        final String itemBaseUrl = Configuration.getValue(BEAGEN_BASEURL) + API_ITEM_METHODE;
        for (String s : Configuration.getValueAsArray(headerKey, "\\n")) {
            s = s.replace("{{date}}", dt);
            s = s.replace("{{id}}", itemBaseUrl + "/" + getId());
            s = s.replace("{{feed}}", itemBaseUrl + "/"
                    + type.name().toLowerCase(Locale.ROOT) + "/"
                    + sector.name().toLowerCase(Locale.ROOT) + "/latest");
            sb.append(s);
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Replaces the snapshot's payload with a defensive copy.
     *
     * @param content gzip-compressed UTF-8 data lines without the generated header
     */
    public void setContent(byte[] content) {
        this.content = Arrays.copyOf(content, content.length);
    }

    @Override
    public int hashCode() {
        return BeaconFile.class.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof BeaconFile other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public String toString() {
        return "de.ddb.labs.beacon.backend.BeaconFile[ id=" + id + " ]";
    }

    /**
     * Returns the number of data entries represented by the payload.
     *
     * @return the stored entry count
     */
    public int getCount() {
        return count;
    }

    /**
     * Sets the number of data entries represented by the payload.
     *
     * @param count entry count, positive for snapshots to be saved
     */
    public void setCount(int count) {
        this.count = count;
    }

    /**
     * Builds a suggested filename from the type and sector.
     * The {@link SECTOR#ALL ALL} snapshot omits the sector suffix.
     *
     * @param withDate whether to prepend the creation date in {@code yyyy-MM-dd} format
     * @return the suggested filename with a {@code .txt} suffix
     */
    public String getFilename(boolean withDate) {
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        final String s = (getSector() == SECTOR.ALL) ? "" : "-" + getSector().getFileName();
        final String t = (getType() == null) ? "" : "-" + getType().getName();
        final String date = withDate ? df.format(getCreated()) + "-" : "";
        return date + "beacon-ddb" + t + s + ".txt";
    }

    /**
     * Compares the decoded payloads line by line for import deduplication.
     * Corresponding comment lines may differ in text. Line order and the number of lines
     * must match. The generated download headers and database metadata are outside the comparison.
     *
     * @param other snapshot to compare
     * @return whether the payloads contain the same data in the same order
     * @throws IOException if either payload cannot be decoded
     */
    public boolean hasSameData(BeaconFile other) throws IOException {
        try (var current = new BufferedReader(new InputStreamReader(openContent(), StandardCharsets.UTF_8));
                var previous = new BufferedReader(new InputStreamReader(other.openContent(), StandardCharsets.UTF_8))) {
            while (true) {
                final String line = current.readLine();
                final String previousLine = previous.readLine();
                if (line == null || previousLine == null) {
                    return line == null && previousLine == null;
                }
                if (!line.equals(previousLine) && !(line.startsWith("#") && previousLine.startsWith("#"))) {
                    return false;
                }
            }
        }
    }

    /**
     * Returns the entity type represented by this snapshot.
     *
     * @return the snapshot's entity type
     */
    public TYPE getType() {
        return type;
    }

    /**
     * Sets the entity type used to classify the snapshot and select the download header.
     *
     * @param type snapshot entity type
     */
    public void setType(TYPE type) {
        this.type = type;
    }

    /**
     * Serializes a snapshot identifier as a permalink under the configured base URL.
     */
    public static class IdSerializer extends ValueSerializer<Long> {

        @Override
        public void serialize(Long t, JsonGenerator jg, SerializationContext context) {
            try {
                jg.writeString(Configuration.getValue(BEAGEN_BASEURL) + API_ITEM_METHODE + "/" + Long.toString(t));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

}
