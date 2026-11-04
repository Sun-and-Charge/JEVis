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

import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Settings of the old data archive (OpenJEVis #1440), read from {@code webservice.archive.*} of the webservice
 * config file. The archive is disabled by default.
 */
public class ArchiveSettings {

    public static final int DEFAULT_OLDER_THAN_YEARS = 5;
    public static final long DEFAULT_MAX_ROWS = 2000000L;
    public static final int DEFAULT_MAX_MINUTES = 60;
    public static final int DEFAULT_BATCH_SIZE = 5000;
    public static final int DEFAULT_PAUSE_MS = 100;
    public static final LocalTime DEFAULT_TIME = LocalTime.of(3, 0);

    private boolean enabled = false;
    private File dir;
    private int olderThanYears = DEFAULT_OLDER_THAN_YEARS;
    private List<String> classes = Collections.singletonList("Data");
    private List<String> attributes = Collections.singletonList("Value");
    private LocalTime time = DEFAULT_TIME;
    private long maxRows = DEFAULT_MAX_ROWS;
    private int maxMinutes = DEFAULT_MAX_MINUTES;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private int pauseMs = DEFAULT_PAUSE_MS;

    /**
     * Splits a comma or semicolon separated list, trims the entries and drops empty ones.
     */
    public static List<String> parseList(String text) {
        List<String> list = new ArrayList<>();
        if (text == null) return list;
        for (String part : text.split("[,;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
        return list;
    }

    /**
     * Parses {@code HH:mm}, falls back to {@link #DEFAULT_TIME} for invalid values.
     */
    public static LocalTime parseTime(String text) {
        if (text == null) return DEFAULT_TIME;
        try {
            return LocalTime.parse(text.trim());
        } catch (Exception ex) {
            return DEFAULT_TIME;
        }
    }

    /**
     * Parses a number, falls back to {@code defaultValue} for missing or invalid values and values below {@code min}.
     */
    public static long parseLong(String text, long defaultValue, long min) {
        if (text == null) return defaultValue;
        try {
            long value = Long.parseLong(text.trim());
            return value < min ? defaultValue : value;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return the archive folder, {@code null} if not configured */
    public File getDir() {
        return dir;
    }

    public void setDir(File dir) {
        this.dir = dir;
    }

    /** @return minimum age in years of archived samples (only complete calendar years are archived) */
    public int getOlderThanYears() {
        return olderThanYears;
    }

    public void setOlderThanYears(int olderThanYears) {
        this.olderThanYears = olderThanYears;
    }

    /** @return JEVis classes whose samples are archived (exact class names, no sub classes) */
    public List<String> getClasses() {
        return classes;
    }

    public void setClasses(List<String> classes) {
        this.classes = classes;
    }

    /** @return attribute names that are archived */
    public List<String> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<String> attributes) {
        this.attributes = attributes;
    }

    /** @return time of day (server time zone) of the daily run */
    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    /** @return the run stops before the next data row/year once this many rows were archived */
    public long getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(long maxRows) {
        this.maxRows = maxRows;
    }

    /** @return the run stops before the next data row/year after this many minutes */
    public int getMaxMinutes() {
        return maxMinutes;
    }

    public void setMaxMinutes(int maxMinutes) {
        this.maxMinutes = maxMinutes;
    }

    /** @return rows per DELETE / INSERT statement */
    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /** @return pause between two batches in milliseconds */
    public int getPauseMs() {
        return pauseMs;
    }

    public void setPauseMs(int pauseMs) {
        this.pauseMs = pauseMs;
    }

    @Override
    public String toString() {
        return "enabled=" + enabled + ", dir=" + dir + ", olderthanyears=" + olderThanYears + ", classes=" + classes
                + ", attributes=" + attributes + ", time=" + time + ", maxrows=" + maxRows + ", maxminutes=" + maxMinutes
                + ", batchsize=" + batchSize + ", pausems=" + pauseMs;
    }
}
