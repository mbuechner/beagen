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
package de.ddb.labs.beagen.api;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;

@Entity
@Table(name = "files")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Files.findAll", query = "SELECT f FROM Files f")
    ,
    @NamedQuery(name = "Files.findById", query = "SELECT f FROM Files f WHERE f.id = :id")
    ,
    @NamedQuery(name = "Files.findByType", query = "SELECT f FROM Files f WHERE f.type = :type")
    ,
    @NamedQuery(name = "Files.findByCreated", query = "SELECT f FROM Files f WHERE f.created = :created")})
public class BeaconFile implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    // @NotNull
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, length = 4, precision = 10, nullable = false)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Basic(optional = false)
    @NotNull
    //@Size(min = 1, max = 32)
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
    @Column(name = "data")
    private byte[] data;

    public BeaconFile() {
    }

    public BeaconFile(Integer id) {
        this.id = id;
    }

    public BeaconFile(SECTOR type, Date created, byte[] data) {
        this.type = type;
        this.created = created;
        this.data = data;
    }

    public BeaconFile(int id, SECTOR type, Date created, byte[] data) {
        this.id = id;
        this.type = type;
        this.created = created;
        this.data = data;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof BeaconFile)) {
            return false;
        }
        final BeaconFile other = (BeaconFile) object;
        return !((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id)));
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

    public enum SECTOR {
        NONE("count", "all", "DDB gesamt", ""),
        SEC_01("count_sec_01", "sec_01", "Archiv", "http://ddb.vocnet.org/sparte/sparte001"),
        SEC_02("count_sec_02", "sec_02", "Bibliothek", "http://ddb.vocnet.org/sparte/sparte001"),
        SEC_03("count_sec_03", "sec_03", "Denkmalpflege", "http://ddb.vocnet.org/sparte/sparte001"),
        SEC_04("count_sec_04", "sec_04", "Forschung", "http://ddb.vocnet.org/sparte/sparte001"),
        SEC_05("count_sec_05", "sec_05", "Mediathek", "http://ddb.vocnet.org/sparte/sparte001"),
        SEC_06("count_sec_06", "sec_06", "Museum", "http://ddb.vocnet.org/sparte/sparte001"),
        SEC_07("count_sec_07", "sec_07", "Sonstige", "http://ddb.vocnet.org/sparte/sparte001");

        private final String name, shortName, humanName, uri;

        SECTOR(String name, String shortName, String humanName, String uri) {
            this.uri = uri;
            this.name = name;
            this.shortName = shortName;
            this.humanName = humanName;
        }

        public String getName() {
            return name;
        }

        public String getShortName() {
            return shortName;
        }

        public String getHumanName() {
            return humanName;
        }

        public String getUri() {
            return uri;
        }
    }
}
