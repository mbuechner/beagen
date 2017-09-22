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
package de.ddb.labs.beagen.beacon;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Entity
@Table(name = "BeaconFile")
//@XmlRootElement
//@NamedQueries({
//    @NamedQuery(name = "Files.findAll", query = "SELECT f FROM Files f"),
//    @NamedQuery(name = "Files.findById", query = "SELECT f FROM Files f WHERE f.id = :id"),
//    @NamedQuery(name = "Files.findByType", query = "SELECT f FROM Files f WHERE f.type = :type"),
//    @NamedQuery(name = "Files.findByCreated", query = "SELECT f FROM Files f WHERE f.created = :created")})
public class BeaconFile implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @NotNull
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, length = 4, precision = 10, nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 32)
    @Column(name = "type")
    private SECTOR type;

    @Basic(optional = false)
    @NotNull
    @Column(name = "created")
    @Temporal(TemporalType.TIMESTAMP)
    private Date created;

    @Basic(optional = false)
    @NotNull
    @Column(name = "count")
    private int count;

    @Basic(optional = false)
    @NotNull
    @Lob
    @Column(name = "content")
    private byte[] content;

    public BeaconFile() {
    }

    public BeaconFile(Long id) {
        this.id = id;
    }

    public BeaconFile(SECTOR type, Date created, byte[] content) {
        this.type = type;
        this.created = created;
        this.content = content;
    }

    public BeaconFile(Long id, SECTOR type, Date created, byte[] content) {
        this.id = id;
        this.type = type;
        this.created = created;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SECTOR getType() {
        return type;
    }

    public void setType(SECTOR type) {
        this.type = type;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
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
        return "de.ddb.pro.beacon.Files[ id=" + id + " ]";
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
        // - date: only if beacon is not the newest, YYYY-MM-DD
        // Example: beacon-ddb-persons.txt, 2014-07-31-beacon-ddb-persons-archive.txt

        final DateFormat df = new SimpleDateFormat("YYYY-MM-dd");
        final String entityType = "-persons";
        final String sector = (getType() == SECTOR.ALL) ? "" : "-" + getType().getFileName();
        final String date = withDate ? df.format(getCreated()) + "-" : "";
        return date + "beacon-ddb" + entityType + sector + ".txt";
    }

    public boolean equals(byte[] otherContent) throws IOException {

        try (final InputStream i1 = new GZIPInputStream(new ByteArrayInputStream(getContent()));
                final InputStream i2 = new GZIPInputStream(new ByteArrayInputStream(otherContent));
                final Reader d1 = new InputStreamReader(i1, Charset.forName("UTF-8"));
                final Reader d2 = new InputStreamReader(i2, Charset.forName("UTF-8"));
                final BufferedReader b1 = new BufferedReader(d1);
                final BufferedReader b2 = new BufferedReader(d2)) {

            while (true) {
                final String l1 = b1.readLine();
                final String l2 = b2.readLine();

                if (l1 == null && l2 == null) {
                    return true; // they are both null (eg. buffer is empty): equal
                } else if ((l1 == null && l2 != null) || (l1 != null && l2 == null)) {
                    return false; // one is null, the other not: not equal
                } else if (!l1.equals(l2) && !(l1.startsWith("#") && l2.startsWith("#"))) {
                    return false; // a line which is not equal and not a comment
                }
            }
        }
    }

    public enum SECTOR {
        ALL("count", "all", "", "", "", ""),
        SEC_01("count_sec_01", "sec_01", "Archiv", "der Kultursparte Archiv ", "archive", "http://ddb.vocnet.org/sparte/sparte001"),
        SEC_02("count_sec_02", "sec_02", "Bibliothek", "der Kultursparte Bibliothek ", "library", "http://ddb.vocnet.org/sparte/sparte002"),
        SEC_03("count_sec_03", "sec_03", "Denkmalpflege", "der Kultursparte Denkmalpflege ", "monument-protection", "http://ddb.vocnet.org/sparte/sparte003"),
        SEC_04("count_sec_04", "sec_04", "Forschung", "der Kultursparte Forschung ", "research", "http://ddb.vocnet.org/sparte/sparte004"),
        SEC_05("count_sec_05", "sec_05", "Mediathek", "der Kultursparte Mediathek ", "media", "http://ddb.vocnet.org/sparte/sparte005"),
        SEC_06("count_sec_06", "sec_06", "Museum", "der Kultursparte Museum ", "museum", "http://ddb.vocnet.org/sparte/sparte006"),
        SEC_07("count_sec_07", "sec_07", "Sonstige", "der Kultursparte Sonstige ", "other", "http://ddb.vocnet.org/sparte/sparte007");

        private final String name, shortName, fileName, humanName, beaconDescName, uri;

        SECTOR(String name, String shortName, String humanName, String beaconDescName, String fileName, String uri) {
            this.uri = uri;
            this.name = name;
            this.shortName = shortName;
            this.humanName = humanName;
            this.fileName = fileName;
            this.beaconDescName = beaconDescName;
        }

        public String getName() {
            return name;
        }

        public String getShortName() {
            return shortName;
        }

        public String getBeaconDescName() {
            return beaconDescName;
        }

        public String getHumanName() {
            return humanName;
        }

        public String getFileName() {
            return fileName;
        }

        public String getUri() {
            return uri;
        }

    }
}
