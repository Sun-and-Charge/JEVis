/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.invoice;

import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

/**
 * Objects, samples and rights of the logged-in user as needed by {@link InvoiceImport}
 * ({@link SqlInvoiceStore} on the database, an in-memory store in the tests).
 */
public interface InvoiceStore {

    /** @return the object if it exists, the user may read it and it is not in the recycle bin, else null */
    JsonObject object(long id) throws Exception;

    /** @return the readable children of the object that are not in the recycle bin */
    List<JsonObject> children(long id) throws Exception;

    /** @return value of the latest sample of the attribute or null */
    String latest(long id, String attribute) throws Exception;

    /** @return the sample of the attribute at exactly this time stamp or null */
    JsonSample sampleAt(long id, String attribute, DateTime timestamp) throws Exception;

    boolean canCreate(JsonObject parent, String jevisClass);

    boolean canWrite(JsonObject object);

    /**
     * Creates an object (it gets the owners of the parent).
     *
     * @param localNames local names by language ("de", "en"), may be empty
     */
    JsonObject create(JsonObject parent, String jevisClass, String name, Map<String, String> localNames) throws Exception;

    /** Writes a sample (replaces a sample at the same time stamp). */
    void setSample(long id, String attribute, int primitiveType, DateTime timestamp, String value, String note) throws Exception;

    /** Sets input and display unit of the attribute (label "kWh", "m³", "€", ...). */
    void setUnit(long id, String attribute, String unit) throws Exception;

    /** @return label of the input unit of the attribute or null */
    String unit(long id, String attribute) throws Exception;

    /** Stores a file sample (file on disk, sample value = file name). */
    void storeFile(long id, String attribute, DateTime timestamp, String fileName, byte[] content, String note) throws Exception;

    /** Removes an object created by the running import (rollback), including its samples and files. */
    void deleteObject(long id) throws Exception;

    /** Removes the sample of the attribute at exactly this time stamp (rollback). */
    void deleteSample(long id, String attribute, DateTime timestamp) throws Exception;
}
