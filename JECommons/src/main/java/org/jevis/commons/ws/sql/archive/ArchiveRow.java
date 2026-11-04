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

import java.util.Objects;

/**
 * One archived sample row (without object and attribute, they are part of the file name). {@code null} columns stay
 * {@code null}, an empty text stays an empty text.
 */
public final class ArchiveRow {

    private final long timestamp;
    private final String value;
    private final String note;
    private final Integer manId;
    private final Long insertTs;

    /**
     * @param timestamp sample time in epoch milliseconds
     * @param value     value column, may be {@code null}
     * @param note      note column, may be {@code null}
     * @param manId     manid column, may be {@code null}
     * @param insertTs  insertts column in epoch milliseconds, may be {@code null}
     */
    public ArchiveRow(long timestamp, String value, String note, Integer manId, Long insertTs) {
        this.timestamp = timestamp;
        this.value = value;
        this.note = note;
        this.manId = manId;
        this.insertTs = insertTs;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getValue() {
        return value;
    }

    public String getNote() {
        return note;
    }

    public Integer getManId() {
        return manId;
    }

    public Long getInsertTs() {
        return insertTs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArchiveRow)) return false;
        ArchiveRow other = (ArchiveRow) o;
        return timestamp == other.timestamp && Objects.equals(value, other.value) && Objects.equals(note, other.note)
                && Objects.equals(manId, other.manId) && Objects.equals(insertTs, other.insertTs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, value, note, manId, insertTs);
    }

    @Override
    public String toString() {
        return "ArchiveRow{" + timestamp + ", value=" + value + ", note=" + note + ", manid=" + manId + ", insertts=" + insertTs + "}";
    }
}
