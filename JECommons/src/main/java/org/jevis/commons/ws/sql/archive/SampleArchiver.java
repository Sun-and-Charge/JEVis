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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.ws.sql.SQLDataSource;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Old data archive (OpenJEVis #1440): writes samples of complete years older than the configured age per data row
 * (object and attribute) and year into gzip compressed CSV files, verifies the files and only then deletes the rows
 * in small batches. Files are restored with {@link #restore(File, Long, String)}.
 * <p>
 * Safety:
 * <ul>
 *     <li>one data row and year at a time; the run stops at the first error</li>
 *     <li>the file is written as {@code .part}, re-read and compared (row count and {@link RowChecksum}) with the rows
 *     read from the database in the same statement, and only then renamed</li>
 *     <li>rows are deleted in batches of {@code batchsize}: each batch is read again with {@code FOR UPDATE} and must
 *     be identical to the next rows of the verified file, otherwise nothing of that batch is deleted</li>
 *     <li>samples with a file ({@code file} or {@code filename} set) are never archived nor deleted</li>
 *     <li>limits on rows and runtime per run, a pause between batches and a lock file against parallel runs</li>
 * </ul>
 */
public class SampleArchiver {

    public static final String MANIFEST_DIR = "manifest";
    static final String LOCK_FILE = ".archive.lock";
    static final long MIN_FREE_SPACE = 512L * 1024 * 1024;
    private static final Logger logger = LogManager.getLogger(SampleArchiver.class);
    private static final DateTimeFormatter RUN_NAME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final String ROW_FILTER = " from sample where object=? and attribute=? and timestamp>=? and timestamp<? and file is null and filename is null";
    private static final String ROW_COLUMNS = "select timestamp,value,note,manid,insertts";

    private final Connection connection;
    private final ArchiveSettings settings;
    private final Consumer<String> progress;

    /**
     * @param connection own database connection, used exclusively by the archiver
     * @param progress   receives progress messages (they are also logged), may be {@code null}
     */
    public SampleArchiver(Connection connection, ArchiveSettings settings, Consumer<String> progress) {
        this.connection = connection;
        this.settings = settings;
        this.progress = progress;
    }

    private static String iso(long ms) {
        return Instant.ofEpochMilli(ms).toString();
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    static ArchiveRow readRow(ResultSet rs) throws SQLException {
        long ts = rs.getTimestamp(1).getTime();
        String value = rs.getString(2);
        String note = rs.getString(3);
        int manId = rs.getInt(4);
        Integer manIdValue = rs.wasNull() ? null : manId;
        Long insertTs;
        try {
            Timestamp insert = rs.getTimestamp(5);
            insertTs = insert == null ? null : insert.getTime();
        } catch (SQLException ex) {
            /* invalid zero date of very old rows */
            insertTs = null;
        }
        return new ArchiveRow(ts, value, note, manIdValue, insertTs);
    }

    /**
     * SHA-256 of a file.
     */
    public static String sha256(File file) throws IOException {
        try (DigestInputStream in = new DigestInputStream(new BufferedInputStream(new FileInputStream(file)), MessageDigest.getInstance("SHA-256"))) {
            byte[] buffer = new byte[65536];
            while (in.read(buffer) != -1) {
                // only the digest is needed
            }
            return RowChecksum.toHex(in.getMessageDigest().digest());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Reads a whole archive file.
     *
     * @return row count and checksum of the file
     * @throws IOException if the file is damaged or not an archive file
     */
    public static RowChecksum verify(File file) throws IOException {
        try (ArchiveCsv.Reader reader = new ArchiveCsv.Reader(new FileInputStream(file))) {
            while (reader.next() != null) {
                // the reader computes the checksum
            }
            return reader.getChecksum();
        }
    }

    private void report(String message) {
        logger.info(message);
        if (progress != null) progress.accept(message);
    }

    /**
     * Lists what an archive run would do, without writing or deleting anything.
     */
    public RunResult dryRun(long nowMs) throws SQLException {
        int cutoffYear = ArchiveUtil.cutoffYear(nowMs, settings.getOlderThanYears());
        long cutoff = ArchiveUtil.yearStart(cutoffYear);
        RunResult result = new RunResult();
        report("Dry run: samples before " + iso(cutoff) + " of classes " + settings.getClasses() + ", attributes "
                + settings.getAttributes() + " (nothing is written or deleted)");
        for (Series series : findSeries(cutoff)) {
            Long first;
            long from = Long.MIN_VALUE;
            while ((first = firstTimestamp(series, from, cutoff)) != null) {
                int year = ArchiveUtil.yearOf(first);
                long start = ArchiveUtil.yearStart(year);
                long until = ArchiveUtil.yearStart(year + 1);
                long rows = countRows(series, start, until);
                result.rows += rows;
                result.files++;
                report(String.format("  %d (%s, %s) %s %d: %d rows", series.object, series.objectName, series.objectClass,
                        series.attribute, year, rows));
                from = until;
            }
        }
        result.status = "dry run";
        report(String.format("Dry run: %d files with %d rows would be archived (per run at most %d rows / %d minutes)",
                result.files, result.rows, settings.getMaxRows(), settings.getMaxMinutes()));
        return result;
    }

    /**
     * Archives and deletes old samples until all are done, a limit is reached or an error occurs.
     *
     * @return the summary; {@link RunResult#error} is set if the run stopped because of an error
     */
    public RunResult archive(long nowMs) throws IOException {
        File dir = settings.getDir();
        if (dir == null) throw new IOException("No archive folder configured (webservice.archive.dir)");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create archive folder " + dir);
        if (!dir.canWrite()) throw new IOException("Archive folder is not writable: " + dir);

        try (FileChannel lockChannel = FileChannel.open(new File(dir, LOCK_FILE).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = tryLock(lockChannel)) {
            if (lock == null) throw new IOException("Another archive run is active (lock " + new File(dir, LOCK_FILE) + ")");
            return archiveLocked(dir, nowMs);
        }
    }

    private FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException ex) {
            return null;
        }
    }

    private RunResult archiveLocked(File dir, long nowMs) throws IOException {
        long started = System.currentTimeMillis();
        int cutoffYear = ArchiveUtil.cutoffYear(nowMs, settings.getOlderThanYears());
        long cutoff = ArchiveUtil.yearStart(cutoffYear);
        RunResult result = new RunResult();
        ArchiveManifest manifest = new ArchiveManifest();
        manifest.started = iso(started);
        manifest.status = "running";
        manifest.cutoff = iso(cutoff);
        manifest.classes = settings.getClasses();
        manifest.attributes = settings.getAttributes();
        File manifestFile = new File(new File(dir, MANIFEST_DIR), "archive-" + RUN_NAME.format(Instant.ofEpochMilli(started)) + ".json");
        result.manifest = manifestFile;
        report("Archive run: samples before " + iso(cutoff) + " of classes " + settings.getClasses() + ", attributes "
                + settings.getAttributes() + " into " + dir);

        try {
            outer:
            for (Series series : findSeries(cutoff)) {
                Long first;
                long from = Long.MIN_VALUE;
                while ((first = firstTimestamp(series, from, cutoff)) != null) {
                    String stop = limitReached(result, started, dir);
                    if (stop != null) {
                        result.status = "limit reached";
                        result.message = stop;
                        break outer;
                    }
                    int year = ArchiveUtil.yearOf(first);
                    long until = ArchiveUtil.yearStart(year + 1);
                    ArchiveManifest.Entry entry = new ArchiveManifest.Entry();
                    manifest.files.add(entry);
                    try {
                        archiveYear(dir, series, year, entry, manifest, manifestFile);
                    } finally {
                        result.rows += entry.rows;
                        result.deletedRows += entry.deletedRows;
                        manifest.rows = result.rows;
                        manifest.deletedRows = result.deletedRows;
                    }
                    if (entry.rows > 0) result.files++;
                    from = until;
                }
            }
            if (result.status == null) result.status = "finished";
        } catch (Exception ex) {
            logger.error("Archive run stopped", ex);
            result.status = "error";
            result.error = ex;
            result.message = ex.getMessage();
        }
        manifest.status = result.status;
        manifest.message = result.message;
        manifest.finished = iso(System.currentTimeMillis());
        if (manifest.files.isEmpty()) {
            File parent = manifestFile.getParentFile();
            if (manifestFile.delete() && parent != null) {
                String[] rest = parent.list();
                if (rest != null && rest.length == 0) parent.delete();
            }
            result.manifest = null;
        } else {
            manifest.write(manifestFile);
        }
        report(String.format("Archive run %s: %d files, %d rows archived, %d rows deleted in %d s%s%s", result.status,
                result.files, result.rows, result.deletedRows, (System.currentTimeMillis() - started) / 1000,
                result.message != null ? " - " + result.message : "",
                result.manifest != null ? ", manifest " + result.manifest : ""));
        return result;
    }

    /**
     * @return the reason to stop before the next data row/year, {@code null} to go on
     */
    String limitReached(RunResult result, long started, File dir) {
        if (result.rows >= settings.getMaxRows()) {
            return "maximum of " + settings.getMaxRows() + " rows per run reached";
        }
        if (System.currentTimeMillis() - started >= settings.getMaxMinutes() * 60000L) {
            return "maximum runtime of " + settings.getMaxMinutes() + " minutes reached";
        }
        if (dir.getUsableSpace() < MIN_FREE_SPACE) {
            return "less than " + (MIN_FREE_SPACE / 1024 / 1024) + " MB free in " + dir;
        }
        return null;
    }

    private void archiveYear(File dir, Series series, int year, ArchiveManifest.Entry entry, ArchiveManifest manifest,
                             File manifestFile) throws Exception {
        long start = ArchiveUtil.yearStart(year);
        long until = ArchiveUtil.yearStart(year + 1);
        File target = ArchiveUtil.nextFreeFile(dir, series.object, series.attribute, year);
        File part = new File(target.getPath() + ".part");
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);

        entry.file = dir.toPath().relativize(target.toPath()).toString().replace(File.separatorChar, '/');
        entry.object = series.object;
        entry.objectName = series.objectName;
        entry.objectClass = series.objectClass;
        entry.attribute = series.attribute;
        entry.year = year;
        entry.from = iso(start);
        entry.until = iso(until);
        entry.status = "error";

        /* 1. write the rows of one statement (consistent read) into the .part file */
        RowChecksum written;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(part));
             ArchiveCsv.Writer writer = new ArchiveCsv.Writer(out);
             PreparedStatement ps = connection.prepareStatement(ROW_COLUMNS + ROW_FILTER + " order by timestamp",
                     ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            ps.setFetchSize(Integer.MIN_VALUE); // stream the rows (MySQL/MariaDB Connector/J)
            setRange(ps, series, start, until);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    writer.write(readRow(rs));
                }
            }
            written = writer.getChecksum();
        } catch (Exception ex) {
            Files.deleteIfExists(part.toPath());
            throw ex;
        }
        if (written.getCount() == 0) {
            Files.deleteIfExists(part.toPath());
            manifest.files.remove(entry);
            return;
        }
        try (FileChannel channel = FileChannel.open(part.toPath(), StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        entry.rows = written.getCount();
        entry.rowChecksum = written.finish();
        entry.firstSample = iso(written.getFirst());
        entry.lastSample = iso(written.getLast());

        /* 2. read the file again and compare */
        RowChecksum read = verify(part);
        String readChecksum = read.finish();
        if (read.getCount() != entry.rows || !readChecksum.equals(entry.rowChecksum)) {
            File failed = new File(target.getPath() + ".failed");
            Files.move(part.toPath(), failed.toPath(), StandardCopyOption.REPLACE_EXISTING);
            entry.message = "verification failed: file has " + read.getCount() + " rows / " + readChecksum + ", database "
                    + entry.rows + " rows / " + entry.rowChecksum;
            manifest.write(manifestFile);
            throw new IOException("Verification of " + failed + " failed, nothing deleted: " + entry.message);
        }
        try {
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(part.toPath(), target.toPath());
        }
        entry.fileSha256 = sha256(target);
        entry.fileSize = target.length();
        entry.status = "verified";
        manifest.write(manifestFile);
        report(String.format("  %s: %d rows written and verified", entry.file, entry.rows));

        /* 3. delete in batches, every batch must match the file */
        try {
            deleteArchivedRows(target, series, entry);
        } catch (Exception ex) {
            entry.status = "error";
            entry.message = "deleting stopped after " + entry.deletedRows + " rows: " + ex.getMessage();
            manifest.write(manifestFile);
            throw ex;
        } finally {
            updateMinMaxTS(series);
        }
        if (entry.deletedRows != entry.rows) {
            entry.status = "error";
            entry.message = "deleted " + entry.deletedRows + " of " + entry.rows + " rows";
            manifest.write(manifestFile);
            throw new IOException(entry.file + ": " + entry.message);
        }
        entry.status = "deleted";
        manifest.write(manifestFile);
        report(String.format("  %s: %d rows deleted from the database", entry.file, entry.deletedRows));
    }

    private void deleteArchivedRows(File file, Series series, ArchiveManifest.Entry entry) throws Exception {
        String select = ROW_COLUMNS + " from sample where object=? and attribute=? and timestamp>=? and timestamp<=?"
                + " and file is null and filename is null order by timestamp for update";
        String delete = "delete from sample where object=? and attribute=? and timestamp>=? and timestamp<=?"
                + " and file is null and filename is null";
        boolean autoCommit = connection.getAutoCommit();
        try (ArchiveCsv.Reader reader = new ArchiveCsv.Reader(new FileInputStream(file))) {
            connection.setAutoCommit(false);
            List<ArchiveRow> batch;
            while (!(batch = reader.nextBatch(settings.getBatchSize())).isEmpty()) {
                long first = batch.get(0).getTimestamp();
                long last = batch.get(batch.size() - 1).getTimestamp();
                try {
                    List<ArchiveRow> current = new ArrayList<>(batch.size());
                    try (PreparedStatement ps = connection.prepareStatement(select)) {
                        setRange(ps, series, first, last);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) current.add(readRow(rs));
                        }
                    }
                    if (!current.equals(batch)) {
                        throw new IOException(String.format("Rows of %d %s between %s and %s changed since they were archived "
                                        + "(database %d rows, file %d rows), nothing of this batch deleted", series.object,
                                series.attribute, iso(first), iso(last), current.size(), batch.size()));
                    }
                    int count;
                    try (PreparedStatement ps = connection.prepareStatement(delete)) {
                        setRange(ps, series, first, last);
                        count = ps.executeUpdate();
                    }
                    if (count != batch.size()) {
                        throw new IOException("Deleted " + count + " instead of " + batch.size() + " rows, rolled back");
                    }
                    connection.commit();
                    entry.deletedRows += count;
                } catch (Exception ex) {
                    connection.rollback();
                    throw ex;
                }
                pause();
            }
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void pause() throws InterruptedException {
        if (settings.getPauseMs() > 0) Thread.sleep(settings.getPauseMs());
    }

    private void setRange(PreparedStatement ps, Series series, long from, long until) throws SQLException {
        ps.setLong(1, series.object);
        ps.setString(2, series.attribute);
        ps.setTimestamp(3, new Timestamp(from));
        ps.setTimestamp(4, new Timestamp(until));
    }

    private void updateMinMaxTS(Series series) {
        try {
            new SQLDataSource(connection).getAttributeTable().updateMinMaxTS(series.object, series.attribute);
        } catch (Exception ex) {
            logger.error("Could not update the time range of {} {}", series.object, series.attribute, ex);
        }
    }

    /**
     * Data rows of the configured classes and attributes that may have samples before the cutoff.
     */
    List<Series> findSeries(long cutoff) throws SQLException {
        List<Series> list = new ArrayList<>();
        if (settings.getClasses().isEmpty() || settings.getAttributes().isEmpty()) return list;
        String sql = "select a.object, a.name, o.name, o.type from attribute a join object o on o.id=a.object"
                + " where o.type in (" + placeholders(settings.getClasses().size()) + ")"
                + " and a.name in (" + placeholders(settings.getAttributes().size()) + ")"
                + " and (a.mints is null or a.mints<?) order by a.object, a.name";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int p = 0;
            for (String c : settings.getClasses()) ps.setString(++p, c);
            for (String a : settings.getAttributes()) ps.setString(++p, a);
            ps.setTimestamp(++p, new Timestamp(cutoff));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Series(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                }
            }
        }
        return list;
    }

    /**
     * @return the first archivable sample time in {@code [from, until)} or {@code null}
     */
    private Long firstTimestamp(Series series, long from, long until) throws SQLException {
        String sql = "select min(timestamp)" + ROW_FILTER;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            /* Long.MIN_VALUE is no valid SQL datetime */
            setRange(ps, series, from == Long.MIN_VALUE ? ArchiveUtil.yearStart(1000) : from, until);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp(1);
                    return ts == null ? null : ts.getTime();
                }
            }
        }
        return null;
    }

    private long countRows(Series series, long from, long until) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select count(*)" + ROW_FILTER)) {
            setRange(ps, series, from, until);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /**
     * Inserts the rows of an archive file again ({@code INSERT IGNORE}: existing samples are kept). The file is read
     * completely before the first row is inserted, so a damaged file inserts nothing.
     *
     * @param object    target object, {@code null} to take it from the file name
     * @param attribute target attribute, {@code null} to take it from the file name
     * @return the number of inserted rows
     */
    public RestoreResult restore(File file, Long object, String attribute) throws Exception {
        ArchiveUtil.FileKey key = ArchiveUtil.parseFileName(file.getName());
        if ((object == null || attribute == null) && key == null) {
            throw new IOException("Object and attribute can not be taken from the file name " + file.getName()
                    + ", give them as options");
        }
        long targetObject = object != null ? object : key.object;
        String targetAttribute = attribute != null ? attribute : key.attribute;
        RestoreResult result = new RestoreResult();
        RowChecksum check = verify(file);
        result.rows = check.getCount();
        result.rowChecksum = check.finish();
        report(String.format("Restore %s into %d %s: %d rows, checksum %s", file, targetObject, targetAttribute,
                result.rows, result.rowChecksum));

        try (PreparedStatement ps = connection.prepareStatement("select count(*) from object where id=?")) {
            ps.setLong(1, targetObject);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getLong(1) == 0) throw new IOException("Object " + targetObject + " does not exist");
            }
        }

        try (ArchiveCsv.Reader reader = new ArchiveCsv.Reader(new FileInputStream(file))) {
            List<ArchiveRow> batch;
            while (!(batch = reader.nextBatch(settings.getBatchSize())).isEmpty()) {
                StringBuilder sql = new StringBuilder("insert ignore into sample (object,attribute,timestamp,value,manid,note,insertts) values ");
                for (int i = 0; i < batch.size(); i++) {
                    sql.append(i == 0 ? "" : ",").append("(?,?,?,?,?,?,?)");
                }
                try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                    int p = 0;
                    for (ArchiveRow row : batch) {
                        ps.setLong(++p, targetObject);
                        ps.setString(++p, targetAttribute);
                        ps.setTimestamp(++p, new Timestamp(row.getTimestamp()));
                        ps.setString(++p, row.getValue());
                        if (row.getManId() == null) ps.setNull(++p, Types.INTEGER);
                        else ps.setInt(++p, row.getManId());
                        ps.setString(++p, row.getNote());
                        if (row.getInsertTs() == null) ps.setNull(++p, Types.TIMESTAMP);
                        else ps.setTimestamp(++p, new Timestamp(row.getInsertTs()));
                    }
                    result.inserted += ps.executeUpdate();
                }
                pause();
            }
        } finally {
            updateMinMaxTS(new Series(targetObject, targetAttribute, null, null));
        }
        report(String.format("Restore %s: %d rows inserted, %d already existed", file.getName(), result.inserted,
                result.rows - result.inserted));
        return result;
    }

    /**
     * Data row: object and attribute.
     */
    static final class Series {
        final long object;
        final String attribute;
        final String objectName;
        final String objectClass;

        Series(long object, String attribute, String objectName, String objectClass) {
            this.object = object;
            this.attribute = attribute;
            this.objectName = objectName;
            this.objectClass = objectClass;
        }
    }

    /**
     * Summary of an archive or dry run.
     */
    public static final class RunResult {
        /** finished, limit reached, error, dry run */
        public String status;
        public String message;
        public Exception error;
        public long files;
        public long rows;
        public long deletedRows;
        /** manifest of the run, {@code null} if nothing was archived */
        public File manifest;
    }

    /**
     * Summary of a restore.
     */
    public static final class RestoreResult {
        public long rows;
        public long inserted;
        public String rowChecksum;
    }
}
