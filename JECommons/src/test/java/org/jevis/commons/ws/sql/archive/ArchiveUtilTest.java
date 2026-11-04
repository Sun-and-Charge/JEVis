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

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class ArchiveUtilTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static long utc(int year, int month, int day, int hour) {
        return LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Test
    public void cutoffKeepsTheYearThatContainsNowMinusYears() {
        assertEquals(2021, ArchiveUtil.cutoffYear(utc(2026, 11, 4, 12), 5));
        assertEquals(2021, ArchiveUtil.cutoffYear(utc(2026, 1, 1, 0), 5));
        assertEquals(2020, ArchiveUtil.cutoffYear(utc(2025, 12, 31, 23), 5));
        assertEquals(2025, ArchiveUtil.cutoffYear(utc(2026, 6, 1, 0), 1));
        assertEquals(utc(2021, 1, 1, 0), ArchiveUtil.yearStart(2021));
    }

    @Test
    public void everyArchivedSampleIsOldEnough() {
        long now = utc(2026, 3, 15, 8);
        long cutoff = ArchiveUtil.yearStart(ArchiveUtil.cutoffYear(now, 5));
        long lastArchived = cutoff - 1;
        assertTrue(ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastArchived), ZoneOffset.UTC)
                .isBefore(ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC).minusYears(5)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void cutoffNeedsAtLeastOneYear() {
        ArchiveUtil.cutoffYear(System.currentTimeMillis(), 0);
    }

    @Test
    public void yearOfUsesUtc() {
        assertEquals(2019, ArchiveUtil.yearOf(utc(2019, 12, 31, 23)));
        assertEquals(2020, ArchiveUtil.yearOf(utc(2020, 1, 1, 0)));
    }

    @Test
    public void fileNameScheme() {
        assertEquals("4711/4711_Value_2019.csv.gz", ArchiveUtil.relativeFileName(4711, "Value", 2019, 1));
        assertEquals("4711/4711_Value_2019-2.csv.gz", ArchiveUtil.relativeFileName(4711, "Value", 2019, 2));
        assertEquals("5/5_Raw%20Data%2FA%5Fb%3A%2A_2018.csv.gz", ArchiveUtil.relativeFileName(5, "Raw Data/A_b:*", 2018, 1));
    }

    @Test
    public void nameEncodingRoundTrip() {
        for (String name : Arrays.asList("Value", "Raw Data/A_b:*", "äöü €", "..", ".hidden", "%41", "a\\b?c\"d<e>f|g")) {
            String encoded = ArchiveUtil.encodeName(name);
            assertTrue(encoded, encoded.matches("[A-Za-z0-9.%-]+"));
            assertFalse(encoded.startsWith("."));
            assertEquals(name, ArchiveUtil.decodeName(encoded));
        }
    }

    @Test
    public void parseFileName() {
        ArchiveUtil.FileKey key = ArchiveUtil.parseFileName("4711_Raw%20Data%5F1_2019-3.csv.gz");
        assertNotNull(key);
        assertEquals(4711, key.object);
        assertEquals("Raw Data_1", key.attribute);
        assertEquals(2019, key.year);
        assertEquals("Value", ArchiveUtil.parseFileName("1_Value_2020.csv.gz").attribute);
        assertNull(ArchiveUtil.parseFileName("1_Value_2020.csv"));
        assertNull(ArchiveUtil.parseFileName("manifest.json"));
        assertNull(ArchiveUtil.parseFileName("x_Value_2020.csv.gz"));
    }

    @Test
    public void nextFreeFileAddsPartNumber() throws IOException {
        File dir = folder.getRoot();
        File first = ArchiveUtil.nextFreeFile(dir, 7, "Value", 2019);
        assertEquals(new File(dir, "7/7_Value_2019.csv.gz"), first);
        assertTrue(first.getParentFile().mkdirs());
        assertTrue(first.createNewFile());
        File second = ArchiveUtil.nextFreeFile(dir, 7, "Value", 2019);
        assertEquals(new File(dir, "7/7_Value_2019-2.csv.gz"), second);
        /* a left over .part file is not overwritten either */
        assertTrue(new File(second.getPath() + ".part").createNewFile());
        assertEquals(new File(dir, "7/7_Value_2019-3.csv.gz"), ArchiveUtil.nextFreeFile(dir, 7, "Value", 2019));
    }

    @Test
    public void delayUntilNextRun() {
        ZoneId zone = ZoneId.of("UTC");
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 4, 1, 30, 0, 0, zone);
        assertEquals(Duration.ofMinutes(90).toMillis(), ArchiveUtil.delayUntil(now, LocalTime.of(3, 0)));
        assertEquals(Duration.ofHours(23).toMillis(), ArchiveUtil.delayUntil(now.withHour(4).withMinute(0), LocalTime.of(3, 0)));
        assertEquals(Duration.ofDays(1).toMillis(), ArchiveUtil.delayUntil(now.withHour(3).withMinute(0), LocalTime.of(3, 0)));
    }

    @Test
    public void settingsParsing() {
        assertEquals(Arrays.asList("Data", "Clean Data"), ArchiveSettings.parseList(" Data, Clean Data;;Data "));
        assertEquals(Collections.emptyList(), ArchiveSettings.parseList(null));
        assertEquals(LocalTime.of(2, 15), ArchiveSettings.parseTime("02:15"));
        assertEquals(ArchiveSettings.DEFAULT_TIME, ArchiveSettings.parseTime("25:00"));
        assertEquals(7, ArchiveSettings.parseLong("7", 5, 1));
        assertEquals(5, ArchiveSettings.parseLong("0", 5, 1));
        assertEquals(5, ArchiveSettings.parseLong("x", 5, 1));
        assertEquals(5, ArchiveSettings.parseLong(null, 5, 1));
        ArchiveSettings defaults = new ArchiveSettings();
        assertFalse(defaults.isEnabled());
        assertEquals(Collections.singletonList("Data"), defaults.getClasses());
        assertEquals(Collections.singletonList("Value"), defaults.getAttributes());
        assertEquals(5000, defaults.getBatchSize());
    }
}
