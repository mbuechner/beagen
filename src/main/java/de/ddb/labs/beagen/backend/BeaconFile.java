/* 
 * Copyright 2019-2021 Michael Büchner, Deutsche Digitale Bibliothek
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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.ddb.labs.beagen.backend.data.SECTOR.SectorSerializer;
import de.ddb.labs.beagen.backend.data.TYPE.TypeSerializer;
import de.ddb.labs.beagen.backend.helper.Configuration;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Beacon file representation
 *
 * @author Michael Büchner
 */
@Entity
@Table(name = "BeaconFile")
public class BeaconFile implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(BeaconFile.class);
    private static final String BEAGEN_BASEURL = "beagen.baseurl";
    private static final String API_ITEM_METHODE = "/item";

    @Id
    @Basic(optional = false)
    //@NotNull
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonSerialize(using = IdSerializer.class)
    @JsonProperty("@id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Basic(optional = false)
    //@Size(min = 1, max = 32)
    @Column(name = "type")
    @JsonSerialize(using = TypeSerializer.class)
    private TYPE type;

    @Enumerated(EnumType.STRING)
    @Basic(optional = false)
    //@Size(min = 1, max = 32)
    @Column(name = "sector")
    @JsonSerialize(using = SectorSerializer.class)
    private SECTOR sector;

    @Basic(optional = false)
    @Column(name = "created")
    @Temporal(TemporalType.TIMESTAMP)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private Date created;

    @Basic(optional = false)
    @Column(name = "count")
    private int count;

    @Basic(optional = false)
    @Lob
    @Column(name = "content")
    @JsonIgnore
    private byte[] content;

    public BeaconFile() {
    }

    public BeaconFile(Long id) {
        this.id = id;
    }

    public BeaconFile(SECTOR sector, Date created, byte[] content) {
        this.sector = sector;
        this.created = new Date(created.getTime());
        this.content = Arrays.copyOf(content, content.length);
    }

    public BeaconFile(Long id, SECTOR sector, Date created, byte[] content) {
        this.id = id;
        this.sector = sector;
        this.created = new Date(created.getTime());
        this.content = Arrays.copyOf(content, content.length);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SECTOR getSector() {
        return sector;
    }

    public void setSector(SECTOR sector) {
        this.sector = sector;
    }

    public Date getCreated() {
        return new Date(created.getTime());
    }

    public void setCreated(Date created) {
        this.created = new Date(created.getTime());
    }

    public byte[] getContent() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            baos.write(getBeaconHeader().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.error("Could not serialze Beacon file header. {}", e.getMessage());
        }

        try (final GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(content))) {
            stream.transferTo(baos);
        } catch (IOException e) {
            LOG.error("Could not decompress Beacon file from database. {}", e.getMessage(), e);
        } finally {
            try {
                baos.close();
            } catch (IOException e) {
                //nothing
            }
        }

        return baos.toByteArray();
    }

    @JsonIgnore
    public InputStream getBeaconFile() throws IOException {
        return new ByteArrayInputStream(getContent());
    }

    private String getBeaconHeader() throws IOException {

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getDefault());
        final String dt = sdf.format(getCreated());

        final StringBuilder sb = new StringBuilder();

        for (String s : Configuration.get().getValueAsArray("beagen.beacon.header." + type.getName().toLowerCase() + "." + sector.getShortName().toLowerCase(), "\\n")) {
            s = s.replace("{{date}}", dt);
            s = s.replace("{{id}}", Configuration.get().getValue(BEAGEN_BASEURL) + API_ITEM_METHODE + "/" + getId());
            s = s.replace("{{feed}}", Configuration.get().getValue(BEAGEN_BASEURL) + API_ITEM_METHODE + "/"
                    + getType().toString().toLowerCase() + "/"
                    + getSector().toString().toLowerCase() + "/latest");
            sb.append(s);
            sb.append("\n");
        }
        return sb.toString();
    }

    public void setContent(byte[] content) {
        this.content = Arrays.copyOf(content, content.length);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof BeaconFile)) {
            return false;
        }
        final BeaconFile other = (BeaconFile) object;
        try {
            return equals(other.getContent());
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "de.ddb.labs.beacon.backend.BeaconFile[ id=" + id + " ]";
    }

    /**
     * @return the count
     */
    public int getCount() {
        return count;
    }

    /**
     * @param count the count to set
     */
    public void setCount(int count) {
        this.count = count;
    }

    public String getFilename(boolean withDate) {
        // Format: {date}-beacon-ddb-{entityType}-{sector}.txt
        // entityType: 'person', 'organization'
        // - sector: only it there's a sector like 'archive', 'libarary', 'media'
        // - date: only if beacon is not the newest, yyyy-MM-DD
        // Example: beacon-ddb-persons.txt, 2014-07-31-beacon-ddb-persons-archive.txt

        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        final String s = (getSector() == SECTOR.ALL) ? "" : "-" + getSector().getFileName();
        final String t = (getType() == null) ? "" : "-" + getType().getName();
        final String date = withDate ? df.format(getCreated()) + "-" : "";
        return date + "beacon-ddb" + t + s + ".txt";
    }

    public boolean equals(byte[] otherContent) throws IOException {

        try (final InputStream i1 = getBeaconFile();
                final InputStream i2 = new ByteArrayInputStream(otherContent);
                final Reader d1 = new InputStreamReader(i1, Charset.forName("UTF-8"));
                final Reader d2 = new InputStreamReader(i2, Charset.forName("UTF-8"));
                final BufferedReader b1 = new BufferedReader(d1);
                final BufferedReader b2 = new BufferedReader(d2)) {

            while (true) {
                final String l1 = b1.readLine();
                final String l2 = b2.readLine();

                if (l1 == null && l2 == null) {
                    return true; // they are both null (eg. buffer is empty): equal
                } else if (l1 == null || l2 == null) {
                    return false; // one is null, the other not: not equal
                } else if (!l1.equals(l2) && !(l1.startsWith("#") && l2.startsWith("#"))) {
                    return false; // a line which is not equal and not a comment
                }
            }
        }
    }

    /**
     * @return the type
     */
    public TYPE getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(TYPE type) {
        this.type = type;
    }

    public static class IdSerializer extends JsonSerializer<Long> {

        @Override
        public void serialize(Long t, JsonGenerator jg, SerializerProvider sp) throws IOException {
            jg.writeString(Configuration.get().getValue(BEAGEN_BASEURL) + API_ITEM_METHODE + "/" + Long.toString(t));
        }
    }

}
