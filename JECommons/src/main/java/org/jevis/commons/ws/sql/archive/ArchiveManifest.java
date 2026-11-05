/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.ws.sql.archive;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON manifest of one archive run in {@code <archive dir>/manifest/archive-<UTC time>.json}. It is rewritten after
 * every file so it is complete also when the run is stopped.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArchiveManifest {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** start of the run, ISO UTC */
    public String started;
    /** end of the run, ISO UTC */
    public String finished;
    /** running, finished, limit reached, error */
    public String status;
    /** error or stop reason */
    public String message;
    /** samples before this time (ISO UTC) were archived */
    public String cutoff;
    public List<String> classes;
    public List<String> attributes;
    public long rows;
    public long deletedRows;
    public List<Entry> files = new ArrayList<>();

    public static ArchiveManifest read(File file) throws IOException {
        return MAPPER.readValue(file, ArchiveManifest.class);
    }

    /**
     * Writes the manifest via a temporary file.
     */
    public void write(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        File tmp = new File(file.getPath() + ".tmp");
        MAPPER.writeValue(tmp, this);
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * One archive file: one data row (object and attribute) and year.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Entry {
        /** path relative to the archive folder, with {@code /} */
        public String file;
        public long object;
        public String objectName;
        public String objectClass;
        public String attribute;
        public int year;
        /** period of the file: from (inclusive) and until (exclusive), ISO UTC */
        public String from;
        public String until;
        /** first and last sample time in the file, ISO UTC */
        public String firstSample;
        public String lastSample;
        public long rows;
        /** SHA-256 over the row contents, see {@link RowChecksum} */
        public String rowChecksum;
        /** SHA-256 of the archive file */
        public String fileSha256;
        public long fileSize;
        public long deletedRows;
        /** verified (file written and checked), deleted (rows removed from the database), error */
        public String status;
        public String message;
    }
}
