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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure helper functions of the old data archive: cutoff, periods and file names.
 */
public final class ArchiveUtil {

    public static final String FILE_SUFFIX = ".csv.gz";
    /**
     * {@code <object>_<encoded attribute>_<year>[-<n>].csv.gz}
     */
    private static final Pattern FILE_NAME = Pattern.compile("^(\\d+)_(.+)_(\\d{4})(?:-(\\d+))?\\.csv\\.gz$");

    private ArchiveUtil() {
    }

    /**
     * Only complete calendar years (UTC) are archived: all samples before January 1st of the year that contains
     * {@code now - olderThanYears}. Every archived sample is therefore at least {@code olderThanYears} old.
     *
     * @return the first year that is kept in the database
     */
    public static int cutoffYear(long nowMs, int olderThanYears) {
        if (olderThanYears < 1) throw new IllegalArgumentException("olderthanyears must be at least 1");
        return Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).getYear() - olderThanYears;
    }

    /** @return January 1st 00:00 UTC of the year in epoch milliseconds */
    public static long yearStart(int year) {
        return LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /** @return the UTC year of the timestamp */
    public static int yearOf(long ms) {
        return Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).getYear();
    }

    /**
     * Encodes an attribute name for a file name: {@code A-Z a-z 0-9 . -} stay, every other UTF-8 byte becomes
     * {@code %XX} (also {@code _} and {@code %}), so the name can be decoded again and is valid on every file system.
     */
    public static String encodeName(String name) {
        StringBuilder sb = new StringBuilder();
        for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-') {
                sb.append((char) c);
            } else {
                sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        String encoded = sb.toString();
        /* "." and ".." are no valid file name parts */
        return encoded.replaceAll("^\\.", "%2E");
    }

    /**
     * Reverses {@link #encodeName(String)}.
     */
    public static String decodeName(String encoded) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '%') {
                if (i + 2 >= encoded.length()) throw new IllegalArgumentException("Invalid encoded name: " + encoded);
                out.write(Integer.parseInt(encoded.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                out.write(c);
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * @param part 1 for the first file of the data row and year, 2 and more if older samples were added after an
     *             earlier archive run
     * @return relative path {@code <object>/<object>_<attribute>_<year>[-<part>].csv.gz}
     */
    public static String relativeFileName(long object, String attribute, int year, int part) {
        return object + "/" + object + "_" + encodeName(attribute) + "_" + year + (part > 1 ? "-" + part : "") + FILE_SUFFIX;
    }

    /**
     * @return the first file name of the data row and year that does not exist yet below {@code dir}
     */
    public static File nextFreeFile(File dir, long object, String attribute, int year) {
        for (int part = 1; ; part++) {
            File file = new File(dir, relativeFileName(object, attribute, year, part));
            if (!file.exists() && !new File(file.getPath() + ".part").exists()) {
                return file;
            }
        }
    }

    /**
     * Object, attribute and year of an archive file name.
     */
    public static final class FileKey {
        public final long object;
        public final String attribute;
        public final int year;

        FileKey(long object, String attribute, int year) {
            this.object = object;
            this.attribute = attribute;
            this.year = year;
        }
    }

    /**
     * @return object, attribute and year of the file name, {@code null} if it is not an archive file name
     */
    public static FileKey parseFileName(String fileName) {
        Matcher m = FILE_NAME.matcher(fileName);
        if (!m.matches()) return null;
        try {
            return new FileKey(Long.parseLong(m.group(1)), decodeName(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * @return milliseconds from {@code now} until the next {@code time} of day in the zone (today or tomorrow)
     */
    public static long delayUntil(ZonedDateTime now, LocalTime time) {
        ZonedDateTime next = now.toLocalDate().atTime(time).atZone(now.getZone());
        if (!next.isAfter(now)) {
            next = now.toLocalDate().plusDays(1).atTime(time).atZone(now.getZone());
        }
        return Duration.between(now, next).toMillis();
    }
}
