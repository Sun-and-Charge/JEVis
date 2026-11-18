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
package org.jevis.commons.driver;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * FTP channel path with date tokens: only the year/month folders that can hold files after the last readout are
 * listed (data volume over VPN), and entries that only look like a date (a folder "temp" for {@code ${D:yyyy}}, other
 * files) are skipped instead of aborting the search.
 */
public class DataSourceHelperFtpTest {

    /** a small FTP server in memory; records which folders were listed */
    static final class FakeFtp extends FTPClient {
        final Map<String, List<FTPFile>> tree = new HashMap<>();
        final List<String> listed = new ArrayList<>();
        String cwd = "/";

        void add(String folder, String name, boolean directory) {
            FTPFile f = new FTPFile();
            f.setName(name);
            f.setType(directory ? FTPFile.DIRECTORY_TYPE : FTPFile.FILE_TYPE);
            tree.computeIfAbsent(folder, k -> new ArrayList<>()).add(f);
        }

        @Override
        public FTPFile[] listFiles(String path) {
            listed.add(path);
            return tree.getOrDefault(path, Collections.emptyList()).toArray(new FTPFile[0]);
        }

        @Override
        public FTPFile[] listFiles() {
            return listFiles(cwd);
        }

        @Override
        public FTPFile[] listDirectories(String path) {
            listed.add(path);
            List<FTPFile> dirs = new ArrayList<>();
            for (FTPFile f : tree.getOrDefault(path, Collections.emptyList())) if (f.isDirectory()) dirs.add(f);
            return dirs.toArray(new FTPFile[0]);
        }

        @Override
        public boolean changeWorkingDirectory(String pathname) {
            cwd = pathname;
            return true;
        }

        @Override
        public String getModificationTime(String pathname) {
            /* the logger writes a file when its name says so */
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]{14})").matcher(pathname);
            return m.find() ? m.group(1) : "20261117000000";
        }

        @Override
        public int getReplyCode() {
            return 226;
        }
    }

    private static FakeFtp server() {
        FakeFtp ftp = new FakeFtp();
        ftp.add("/ext/Log/", "2025", true);
        ftp.add("/ext/Log/", "2026", true);
        ftp.add("/ext/Log/", "temp", true);
        ftp.add("/ext/Log/", "info.txt", false);
        ftp.add("/ext/Log/2025/", "12", true);
        ftp.add("/ext/Log/2026/", "10", true);
        ftp.add("/ext/Log/2026/", "11", true);
        ftp.add("/ext/Log/2026/", "xx", true);
        ftp.add("/ext/Log/2026/10/", "Meter_20261031230000.csv", false);
        ftp.add("/ext/Log/2026/11/", "Meter_20261109000000.csv", false);
        ftp.add("/ext/Log/2026/11/", "Meter_20261111014530.csv", false);
        ftp.add("/ext/Log/2026/11/", "Event_20261111014530.csv", false);
        ftp.add("/ext/Log/2026/11/", "Meter_backup.csv", false);
        /* a database file of the logger in the same folder (the date part cannot be parsed) */
        ftp.add("/ext/Log/2026/11/", "Meter_61111014530.db", false);
        ftp.add("/ext/Log/2026/11/", "Meter_2026111101.db", false);
        return ftp;
    }

    private static final DateTime LAST_READOUT = new DateTime(2026, 11, 10, 0, 0, DateTimeZone.UTC);

    @Test
    public void dateTokensListOnlyTheFoldersAfterTheLastReadout() {
        FakeFtp ftp = server();
        List<String> files = DataSourceHelper.getFTPMatchedFileNames(ftp, LAST_READOUT, null, DateTimeZone.UTC,
                "/ext/Log/${D:yyyy}/${D:MM}/Meter_${D:yyyyMMddHHmmss}.csv", false);

        assertEquals(Collections.singletonList("/ext/Log/2026/11/Meter_20261111014530.csv"), files);
        /* 2025 and 2026/10 end before the last readout: not listed */
        assertFalse(ftp.listed.toString(), ftp.listed.contains("/ext/Log/2025/"));
        assertFalse(ftp.listed.toString(), ftp.listed.contains("/ext/Log/2026/10/"));
        assertTrue(ftp.listed.contains("/ext/Log/2026/11/"));
    }

    @Test
    public void wildcardFoldersListEverything() {
        /* the configuration in use before: every folder is listed in every run */
        FakeFtp ftp = server();
        DataSourceHelper.getFTPMatchedFileNames(ftp, LAST_READOUT, null, DateTimeZone.UTC,
                "/ext/Log/(.*)/(.*)/Meter(.*)csv", false);
        assertTrue(ftp.listed.toString(), ftp.listed.contains("/ext/Log/2025/"));
        assertTrue(ftp.listed.toString(), ftp.listed.contains("/ext/Log/2026/10/"));
    }

    @Test
    public void maxReadoutWindowStartsAtTheFirstNewFile() {
        /* the logger stopped from 08:31 to 14:45; the last readout lies in that gap */
        FakeFtp ftp = new FakeFtp();
        ftp.add("/ext/Log/", "2026", true);
        ftp.add("/ext/Log/2026/", "11", true);
        for (String time : new String[]{"20261117083148", "20261117144530", "20261117150141", "20261117161530"}) {
            ftp.add("/ext/Log/2026/11/", "Meter_" + time + ".csv", false);
        }
        DateTime lastReadout = new DateTime(2026, 11, 17, 9, 44, DateTimeZone.UTC);
        String path = "/ext/Log/${D:yyyy}/${D:MM}/Meter_${D:yyyyMMddHHmmss}.csv";

        /* one hour from the first new file (before, the window ended at 10:44 and nothing was ever read again) */
        assertEquals(java.util.Arrays.asList("/ext/Log/2026/11/Meter_20261117144530.csv", "/ext/Log/2026/11/Meter_20261117150141.csv"),
                DataSourceHelper.getFTPMatchedFileNames(ftp, lastReadout, 3600L, DateTimeZone.UTC, path, false));
        assertEquals(3, DataSourceHelper.getFTPMatchedFileNames(ftp, lastReadout, null, DateTimeZone.UTC, path, false).size());
    }
}
