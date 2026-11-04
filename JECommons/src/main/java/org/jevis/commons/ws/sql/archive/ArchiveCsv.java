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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Archive file format: gzip compressed CSV (RFC 4180, UTF-8, LF line ends) with the header
 * {@code timestamp,value,note,manid,insertts}.
 * <ul>
 *     <li>{@code timestamp} and {@code insertts}: ISO-8601 in UTC, e.g. {@code 2019-03-01T00:15:00Z}</li>
 *     <li>{@code value} and {@code note}: always in double quotes, quotes doubled; line breaks are kept inside the
 *     quotes</li>
 *     <li>an empty field without quotes is SQL {@code NULL}, {@code ""} is an empty text</li>
 * </ul>
 */
public final class ArchiveCsv {

    public static final String HEADER = "timestamp,value,note,manid,insertts";
    private static final int COLUMNS = 5;

    private ArchiveCsv() {
    }

    static String quote(String text) {
        if (text == null) return "";
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    /**
     * Formats a row as one CSV record (without line end).
     */
    public static String format(ArchiveRow row) {
        return Instant.ofEpochMilli(row.getTimestamp()).toString()
                + ',' + quote(row.getValue())
                + ',' + quote(row.getNote())
                + ',' + (row.getManId() == null ? "" : row.getManId().toString())
                + ',' + (row.getInsertTs() == null ? "" : Instant.ofEpochMilli(row.getInsertTs()).toString());
    }

    /**
     * Writes rows into a gzip compressed archive file and computes their {@link RowChecksum}.
     */
    public static final class Writer implements Closeable {
        private final BufferedWriter out;
        private final RowChecksum checksum = new RowChecksum();
        private Long lastTimestamp;

        public Writer(OutputStream target) throws IOException {
            out = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(target, 65536), StandardCharsets.UTF_8), 65536);
            out.write(HEADER);
            out.write('\n');
        }

        /**
         * @throws IOException if the rows are not in ascending timestamp order
         */
        public void write(ArchiveRow row) throws IOException {
            if (lastTimestamp != null && row.getTimestamp() <= lastTimestamp) {
                throw new IOException("Rows are not in ascending timestamp order at " + Instant.ofEpochMilli(row.getTimestamp()));
            }
            lastTimestamp = row.getTimestamp();
            out.write(format(row));
            out.write('\n');
            checksum.add(row);
        }

        public RowChecksum getChecksum() {
            return checksum;
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    /**
     * Reads an archive file row by row.
     */
    public static final class Reader implements Closeable {
        private final java.io.Reader in;
        private final RowChecksum checksum = new RowChecksum();
        private long line = 1;
        private boolean finished = false;

        public Reader(InputStream source) throws IOException {
            in = new BufferedReader(new InputStreamReader(new GZIPInputStream(source, 65536), StandardCharsets.UTF_8), 65536);
            List<String> header = readRecord();
            if (header == null || header.size() != COLUMNS || !HEADER.equals(String.join(",", header))) {
                throw new IOException("Not a JEVis sample archive file (unexpected header)");
            }
        }

        /**
         * @return the next row or {@code null} at the end of the file
         * @throws IOException for malformed records
         */
        public ArchiveRow next() throws IOException {
            if (finished) return null;
            long startLine = line;
            List<String> fields = readRecord();
            if (fields == null) {
                finished = true;
                return null;
            }
            if (fields.size() != COLUMNS) {
                throw new IOException("Line " + startLine + ": expected " + COLUMNS + " columns but found " + fields.size());
            }
            try {
                if (fields.get(0) == null) throw new IOException("Line " + startLine + ": missing timestamp");
                long ts = Instant.parse(fields.get(0)).toEpochMilli();
                Integer manId = fields.get(3) == null ? null : Integer.valueOf(fields.get(3));
                Long insertTs = fields.get(4) == null ? null : Instant.parse(fields.get(4)).toEpochMilli();
                ArchiveRow row = new ArchiveRow(ts, fields.get(1), fields.get(2), manId, insertTs);
                checksum.add(row);
                return row;
            } catch (DateTimeParseException | NumberFormatException ex) {
                throw new IOException("Line " + startLine + ": " + ex.getMessage(), ex);
            }
        }

        /**
         * @return up to {@code size} rows, an empty list at the end of the file
         */
        public List<ArchiveRow> nextBatch(int size) throws IOException {
            List<ArchiveRow> batch = new ArrayList<>(Math.min(size, 10000));
            ArchiveRow row;
            while (batch.size() < size && (row = next()) != null) {
                batch.add(row);
            }
            return batch;
        }

        /** @return count and checksum of the rows read so far */
        public RowChecksum getChecksum() {
            return checksum;
        }

        /**
         * Reads one record. Unquoted empty fields are returned as {@code null}.
         *
         * @return the fields or {@code null} at the end of the input
         */
        private List<String> readRecord() throws IOException {
            int c = in.read();
            if (c == -1) return null;
            List<String> fields = new ArrayList<>(COLUMNS);
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            while (true) {
                if (c == '"' && field.length() == 0 && !quoted) {
                    quoted = true;
                    while (true) {
                        c = in.read();
                        if (c == -1) throw new IOException("Line " + line + ": unterminated quoted field");
                        if (c == '"') {
                            c = in.read();
                            if (c == '"') {
                                field.append('"');
                            } else {
                                break;
                            }
                        } else {
                            if (c == '\n') line++;
                            field.append((char) c);
                        }
                    }
                    if (c != ',' && c != '\n' && c != '\r' && c != -1) {
                        throw new IOException("Line " + line + ": unexpected character after quoted field");
                    }
                    continue;
                }
                if (c == ',' || c == '\n' || c == '\r' || c == -1) {
                    fields.add(quoted || field.length() > 0 ? field.toString() : null);
                    field.setLength(0);
                    quoted = false;
                    if (c == ',') {
                        c = in.read();
                        continue;
                    }
                    if (c == '\r') {
                        in.mark(1);
                        if (in.read() != '\n') in.reset();
                    }
                    line++;
                    return fields;
                }
                if (quoted) throw new IOException("Line " + line + ": unexpected character after quoted field");
                field.append((char) c);
                c = in.read();
            }
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
