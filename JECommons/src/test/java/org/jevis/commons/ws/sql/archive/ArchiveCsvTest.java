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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

public class ArchiveCsvTest {

    private static final long T0 = ArchiveUtil.yearStart(2019);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static List<ArchiveRow> sampleRows() {
        return Arrays.asList(
                new ArchiveRow(T0, "12.5", null, null, T0 + 1000),
                new ArchiveRow(T0 + 900000, "", "", 0, null),
                new ArchiveRow(T0 + 1800000, null, "note, with comma", 7, T0),
                new ArchiveRow(T0 + 2700000, "say \"hi\"\r\nnext line\nand\rmore", "multi\nline \"note\"", -1, T0 + 5),
                new ArchiveRow(T0 + 3600000, "Umlaute äöü ß € 😀 ,;\t", "\"", null, null),
                new ArchiveRow(T0 + 3600001, "\"\"", ",", 12345, T0 + 999));
    }

    private static byte[] write(List<ArchiveRow> rows, RowChecksum[] checksum) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ArchiveCsv.Writer writer = new ArchiveCsv.Writer(out)) {
            for (ArchiveRow row : rows) writer.write(row);
            if (checksum != null) checksum[0] = writer.getChecksum();
        }
        return out.toByteArray();
    }

    private static List<ArchiveRow> readAll(byte[] data, RowChecksum[] checksum) throws IOException {
        List<ArchiveRow> rows = new ArrayList<>();
        try (ArchiveCsv.Reader reader = new ArchiveCsv.Reader(new ByteArrayInputStream(data))) {
            ArchiveRow row;
            while ((row = reader.next()) != null) rows.add(row);
            if (checksum != null) checksum[0] = reader.getChecksum();
        }
        return rows;
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStream gz = new GZIPOutputStream(out)) {
            gz.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static String gunzip(byte[] data) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void roundTripKeepsSpecialCharactersNullsAndEmptyTexts() throws IOException {
        RowChecksum[] written = new RowChecksum[1];
        RowChecksum[] read = new RowChecksum[1];
        List<ArchiveRow> rows = sampleRows();
        List<ArchiveRow> back = readAll(write(rows, written), read);
        assertEquals(rows, back);
        assertNull(back.get(0).getNote());
        assertEquals("", back.get(1).getNote());
        assertNull(back.get(2).getValue());
        assertEquals(rows.size(), read[0].getCount());
        assertEquals(written[0].getCount(), read[0].getCount());
        assertEquals(Long.valueOf(T0), read[0].getFirst());
        assertEquals(Long.valueOf(T0 + 3600001), read[0].getLast());
        assertEquals(written[0].finish(), read[0].finish());
    }

    @Test
    public void fileIsReadableCsvWithIsoUtcTimestamps() throws IOException {
        String text = gunzip(write(Arrays.asList(
                new ArchiveRow(T0, "1.5", null, 3, T0 + 61000),
                new ArchiveRow(T0 + 500, "a\"b", "", null, null)), null));
        assertEquals("timestamp,value,note,manid,insertts\n"
                + "2019-01-01T00:00:00Z,\"1.5\",,3,2019-01-01T00:01:01Z\n"
                + "2019-01-01T00:00:00.500Z,\"a\"\"b\",\"\",,\n", text);
    }

    @Test
    public void readerAcceptsCrLfLineEnds() throws IOException {
        byte[] data = gzip("timestamp,value,note,manid,insertts\r\n2019-01-01T00:00:00Z,\"1\",,,\r\n2019-01-01T00:15:00Z,2,x,1,\r\n");
        List<ArchiveRow> rows = readAll(data, null);
        assertEquals(2, rows.size());
        assertEquals("2", rows.get(1).getValue());
        assertEquals("x", rows.get(1).getNote());
        assertEquals(Integer.valueOf(1), rows.get(1).getManId());
    }

    @Test
    public void emptyFileHasOnlyTheHeader() throws IOException {
        RowChecksum[] read = new RowChecksum[1];
        assertTrue(readAll(write(new ArrayList<>(), null), read).isEmpty());
        assertEquals(0, read[0].getCount());
        assertEquals(new RowChecksum().finish(), read[0].finish());
    }

    @Test(expected = IOException.class)
    public void writerRejectsUnsortedRows() throws IOException {
        write(Arrays.asList(new ArchiveRow(T0 + 1, "1", null, null, null), new ArchiveRow(T0, "2", null, null, null)), null);
    }

    @Test(expected = IOException.class)
    public void readerRejectsWrongHeader() throws IOException {
        readAll(gzip("ts,value\n2019-01-01T00:00:00Z,1\n"), null);
    }

    @Test(expected = IOException.class)
    public void readerRejectsMissingColumns() throws IOException {
        readAll(gzip(ArchiveCsv.HEADER + "\n2019-01-01T00:00:00Z,\"1\",,\n"), null);
    }

    @Test(expected = IOException.class)
    public void readerRejectsUnterminatedQuote() throws IOException {
        readAll(gzip(ArchiveCsv.HEADER + "\n2019-01-01T00:00:00Z,\"1,,,\n"), null);
    }

    @Test(expected = IOException.class)
    public void readerRejectsInvalidTimestamp() throws IOException {
        readAll(gzip(ArchiveCsv.HEADER + "\n2019-01-01 00:00:00,\"1\",,,\n"), null);
    }

    @Test(expected = IOException.class)
    public void truncatedFileIsDetected() throws IOException {
        List<ArchiveRow> rows = new ArrayList<>();
        for (int i = 0; i < 5000; i++) rows.add(new ArchiveRow(T0 + i * 1000L, "value " + i, null, null, null));
        byte[] data = write(rows, null);
        readAll(Arrays.copyOf(data, data.length / 2), null);
    }

    @Test
    public void checksumDetectsChangedRows() {
        List<ArchiveRow> rows = sampleRows();
        String original = checksum(rows);
        assertEquals(original, checksum(rows));

        List<ArchiveRow> changedValue = new ArrayList<>(rows);
        changedValue.set(0, new ArchiveRow(T0, "12.6", null, null, T0 + 1000));
        assertNotEquals(original, checksum(changedValue));

        List<ArchiveRow> nullInsteadOfEmpty = new ArrayList<>(rows);
        nullInsteadOfEmpty.set(1, new ArchiveRow(T0 + 900000, null, "", 0, null));
        assertNotEquals(original, checksum(nullInsteadOfEmpty));

        List<ArchiveRow> missingRow = new ArrayList<>(rows.subList(0, rows.size() - 1));
        assertNotEquals(original, checksum(missingRow));

        /* moving text between columns must change the checksum */
        assertNotEquals(checksum(Arrays.asList(new ArchiveRow(T0, "ab", "c", null, null))),
                checksum(Arrays.asList(new ArchiveRow(T0, "a", "bc", null, null))));
    }

    private static String checksum(List<ArchiveRow> rows) {
        RowChecksum checksum = new RowChecksum();
        for (ArchiveRow row : rows) checksum.add(row);
        return checksum.finish();
    }

    @Test
    public void readerReturnsBatches() throws IOException {
        List<ArchiveRow> rows = new ArrayList<>();
        for (int i = 0; i < 12; i++) rows.add(new ArchiveRow(T0 + i, String.valueOf(i), null, null, null));
        try (ArchiveCsv.Reader reader = new ArchiveCsv.Reader(new ByteArrayInputStream(write(rows, null)))) {
            List<Integer> sizes = new ArrayList<>();
            List<ArchiveRow> all = new ArrayList<>();
            List<ArchiveRow> batch;
            while (!(batch = reader.nextBatch(5)).isEmpty()) {
                sizes.add(batch.size());
                all.addAll(batch);
            }
            assertEquals(Arrays.asList(5, 5, 2), sizes);
            assertEquals(rows, all);
            assertTrue(reader.nextBatch(5).isEmpty());
        }
    }

    @Test
    public void verifyAndShaOfFile() throws IOException {
        File file = folder.newFile("1_Value_2019.csv.gz");
        List<ArchiveRow> rows = sampleRows();
        RowChecksum[] written = new RowChecksum[1];
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(write(rows, written));
        }
        RowChecksum verified = SampleArchiver.verify(file);
        assertEquals(rows.size(), verified.getCount());
        assertEquals(written[0].finish(), verified.finish());
        assertEquals(64, SampleArchiver.sha256(file).length());
        assertEquals(SampleArchiver.sha256(file), SampleArchiver.sha256(file));
    }
}
