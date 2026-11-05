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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Row count and SHA-256 over the content of archived rows, independent of the CSV text: the same rows give the same
 * checksum whether they are read from the database or from the archive file. Every column is hashed with a
 * null marker and its length, so {@code null} and an empty text and shifted separators give different checksums.
 */
public final class RowChecksum {

    private final MessageDigest digest;
    private final ByteBuffer number = ByteBuffer.allocate(8);
    private long count = 0;
    private Long first;
    private Long last;

    public RowChecksum() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Hex representation of a digest.
     */
    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public void add(ArchiveRow row) {
        addLong(row.getTimestamp());
        addText(row.getValue());
        addText(row.getNote());
        addNullableLong(row.getManId() == null ? null : row.getManId().longValue());
        addNullableLong(row.getInsertTs());
        count++;
        if (first == null) first = row.getTimestamp();
        last = row.getTimestamp();
    }

    private void addLong(long value) {
        number.clear();
        number.putLong(value);
        digest.update(number.array(), 0, 8);
    }

    private void addNullableLong(Long value) {
        if (value == null) {
            digest.update((byte) 0);
        } else {
            digest.update((byte) 1);
            addLong(value);
        }
    }

    private void addText(String text) {
        if (text == null) {
            digest.update((byte) 0);
        } else {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) 1);
            addLong(bytes.length);
            digest.update(bytes);
        }
    }

    public long getCount() {
        return count;
    }

    /** @return timestamp of the first added row, {@code null} if none */
    public Long getFirst() {
        return first;
    }

    /** @return timestamp of the last added row, {@code null} if none */
    public Long getLast() {
        return last;
    }

    /**
     * Finishes the checksum; the object must not be used afterwards.
     *
     * @return the SHA-256 as lower case hex
     */
    public String finish() {
        return toHex(digest.digest());
    }
}
