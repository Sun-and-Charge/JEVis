/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of CSV-Driver.
 * <p>
 * CSV-Driver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * CSV-Driver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * CSV-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * CSV-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.csvparser;

import org.jevis.commons.driver.Result;
import org.jevis.commons.driver.inputHandler.GenericConverter;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CSVParserTest {

    @Test
    void valueFromNewerInputStreamReplacesDuplicateTimestamp() {
        CSVParser parser = createParser("ROW", 0, 0, "yyyy-MM-dd HH:mm:ss");
        InputStream olderFile = csv("Timestamp;Value\n2026-10-06 10:00:00;1.0\n");
        InputStream newerFile = csv("Timestamp;Value\n2026-10-06 10:00:00;2.0\n");

        parser.parse(Arrays.asList(olderFile, newerFile), DateTimeZone.UTC);

        List<Result> results = parser.getResult();
        assertEquals(1, results.size());
        assertEquals(2.0d, results.get(0).getValue());
    }

    @Test
    void rowAlignmentReadsTimeAxisFromTopToBottom() {
        CSVParser parser = createParser("ROW", 0, 1, "dd.MM.yyyy");

        parser.parse(Collections.singletonList(csv(
                "Name;Date;Value\n" +
                        ";02.10.2026;10.0\n" +
                        ";03.10.2026;11.0\n")), DateTimeZone.UTC);

        List<Result> results = parser.getResult();
        assertEquals(2, results.size());
        assertEquals("2026-10-02", results.get(0).getDate().toString("yyyy-MM-dd"));
        assertEquals(10.0d, results.get(0).getValue());
        assertEquals("2026-10-03", results.get(1).getDate().toString("yyyy-MM-dd"));
        assertEquals(11.0d, results.get(1).getValue());
    }

    @Test
    void columnAlignmentReadsTimeAxisFromLeftToRight() {
        CSVParser parser = createParser("COLUMN", 0, 0, "dd.MM.yyyy");

        parser.parse(Collections.singletonList(csv(
                "Name;02.10.2026;03.10.2026\n" +
                        "Value;10.0;11.0\n")), DateTimeZone.UTC);

        List<Result> results = parser.getResult();
        assertEquals(2, results.size());
        assertEquals("2026-10-02", results.get(0).getDate().toString("yyyy-MM-dd"));
        assertEquals(10.0d, results.get(0).getValue());
        assertEquals("2026-10-03", results.get(1).getDate().toString("yyyy-MM-dd"));
        assertEquals(11.0d, results.get(1).getValue());
    }

    /* one record per meter as the M-Bus gateways write it (neutral device ids): Index;Timestamp;DeviceId;…;Value0 */
    private static final String HEADER = "Index;Timestamp;DeviceId;Link;User;Index0;Value0;Unit0\n";

    private static String meterRow(int index, String time, String device, String value) {
        return index + ";" + time + ";" + device + ";0;;0;" + value + ";Wh\n";
    }

    private static DataPoint meter(String device, String target) {
        DataPoint dataPoint = new DataPoint();
        dataPoint.setMappingIdentifier(device);
        dataPoint.setValueIndex(6);
        dataPoint.setTarget(target);
        return dataPoint;
    }

    private CSVParser recordParser(String alignment, DataPoint... dataPoints) {
        CSVParser parser = createParser(alignment, 2, 1, "yyyy-MM-dd HH:mm:ss");
        parser.setHeaderLines(1);
        parser.setDecimalSeparator(",");
        parser.setDataPoints(Arrays.asList(dataPoints));
        return parser;
    }

    @Test
    void columnWithIdentifierAndValueIndexReadsOneRecordPerRowAsBefore2026_10() {
        CSVParser parser = recordParser("COLUMN", meter("DME-00000001-64-4", "1:Value"), meter("DME-00000002-64-4", "2:Value"));
        assertTrue(parser.isRecordLayout());

        parser.parse(Collections.singletonList(csv(HEADER
                + meterRow(0, "2026-09-01 01:44:49", "DME-00000001-64-4", "23863000")
                + meterRow(1, "2026-09-01 01:44:49", "DME-00000002-64-4", "35674000"))), DateTimeZone.UTC);

        List<Result> results = parser.getResult();
        assertEquals(2, results.size());
        assertEquals("1:Value", results.get(0).getTargetStr());
        assertEquals(23863000d, results.get(0).getValue());
        assertEquals("2:Value", results.get(1).getTargetStr());
        assertEquals(35674000d, results.get(1).getValue());
        assertTrue(parser.getReport().errors().isEmpty());
    }

    @Test
    void aFileWithOnlyOneOfTheMetersIsNoError() {
        CSVParser parser = recordParser("COLUMN", meter("DME-00000001-64-4", "1:Value"), meter("DME-00000002-64-4", "2:Value"));

        parser.parse(Collections.singletonList(csv(HEADER
                + meterRow(0, "2026-09-01 01:44:49", "DME-00000002-64-4", "35674000")
                + meterRow(1, "2026-09-01 01:44:49", "DME-99999999-64-4", "1"))), DateTimeZone.UTC);

        List<Result> results = parser.getResult();
        assertEquals(1, results.size());
        assertEquals("2:Value", results.get(0).getTargetStr());
        /* "delete after successful parsing" depends on this: a missing or unknown meter is no error */
        assertTrue(parser.getReport().errors().isEmpty());
    }

    @Test
    void theSameMeterInTwoFilesKeepsTheNewerValue() {
        CSVParser parser = recordParser("COLUMN", meter("DME-00000001-64-4", "1:Value"));

        parser.parse(Arrays.asList(
                csv(HEADER + meterRow(0, "2026-09-01 01:44:49", "DME-00000001-64-4", "100")),
                csv(HEADER + meterRow(0, "2026-09-01 01:44:49", "DME-00000001-64-4", "101"))), DateTimeZone.UTC);

        List<Result> results = parser.getResult();
        assertEquals(1, results.size());
        assertEquals(101d, results.get(0).getValue());
        assertTrue(parser.getReport().errors().isEmpty());
    }

    @Test
    void rowWithIdentifierAndValueIndexFiltersTheRowsToo() {
        CSVParser parser = recordParser("ROW", meter("DME-00000001-64-4", "1:Value"), meter("DME-00000002-64-4", "2:Value"));
        /* ROW: the Datapoint Index is a row; here row 3 (index 2) is a record and must not be skipped as header */
        parser.setDpIndex(0);
        assertFalse(parser.isRecordLayout());

        parser.parse(Collections.singletonList(csv(HEADER
                + meterRow(0, "2026-09-01 01:44:49", "DME-00000001-64-4", "5")
                + meterRow(1, "2026-09-01 01:44:49", "DME-00000002-64-4", "7"))), DateTimeZone.UTC);

        List<Result> results = parser.getResult();
        assertEquals(2, results.size());
        assertEquals(5d, results.get(0).getValue());
        assertEquals("1:Value", results.get(0).getTargetStr());
        assertEquals(7d, results.get(1).getValue());
        assertEquals("2:Value", results.get(1).getTargetStr());
    }

    private CSVParser createParser(String alignment, int datapointIndex, int dateIndex, String dateFormat) {
        DataPoint dataPoint = new DataPoint();
        dataPoint.setMappingIdentifier("Value");
        dataPoint.setTarget("1234:Value");

        CSVParser parser = new CSVParser();
        parser.setConverter(new GenericConverter());
        parser.setCharset(StandardCharsets.UTF_8);
        parser.setDelimiter(";");
        parser.setQuote(null);
        parser.setHeaderLines(0);
        parser.setDpType(alignment);
        parser.setDpIndex(datapointIndex);
        parser.setDateIndex(dateIndex);
        parser.setTimeIndex(null);
        parser.setDateFormat(dateFormat);
        parser.setTimeFormat(null);
        parser.setDecimalSeparator(".");
        parser.setDataPoints(Collections.singletonList(dataPoint));
        return parser;
    }

    private InputStream csv(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}