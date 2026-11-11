/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisFile;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.JEVisFileImp;
import org.jevis.jeapi.ws.InvoiceRequestException;
import org.jevis.jeapi.ws.JEVisDataSourceWS;
import org.jevis.jedemo.ReplayPlanner.Offset;
import org.jevis.jedemo.ReplayPlanner.Sample;
import org.joda.time.DateTime;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One cycle of the demo job against JEVis: replay raw data, roll module dates, create the monthly invoices. Only
 * objects below the configured demo organisations are written (plus "Shifted Until" / "Replay State" on the service
 * object). With {@code dryRun} nothing is written, the job only logs what it would do.
 */
public class DemoJob {

    static final Set<String> ROW_CLASSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("Data", "String Data")));
    static final String CLEAN_DATA = "Clean Data";
    static final String CONVERSION_TO_DIFFERENTIAL = "Conversion to Differential";
    static final String CALC_OUTPUT_CLASS = "Output";
    static final String CALC_OUTPUT_ATTRIBUTE = "Output";
    static final List<String> CHANNEL_TARGET_ATTRIBUTES = Arrays.asList("Target", "Target ID", "Target Id");
    static final String LINK_CLASS = "Link";
    static final String INVOICE_DIRECTORY_CLASS = "Energy Invoices Directory";
    static final String NOTES_CLASS = "Data Notes";
    static final List<String> NOTES_ATTRIBUTES = Arrays.asList("Value", "Tag", "User");
    static final int WRITE_CHUNK = 5000;
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private static final Logger logger = LogManager.getLogger(DemoJob.class);

    private final JEVisDataSource ds;
    private final boolean dryRun;
    private final Clock clock;
    /**
     * last billing month for which both invoices were handled (created or already present)
     */
    private YearMonth invoicedMonth;

    public DemoJob(JEVisDataSource ds, boolean dryRun, Clock clock) {
        this.ds = ds;
        this.dryRun = dryRun;
        this.clock = clock;
    }

    /**
     * One cycle with the settings of the service object.
     *
     * @param service               the JEDemo service object
     * @param organisationsOverride organisation ids to use instead of the configured ones (single mode), or null
     */
    public void runCycle(JEVisObject service, List<Long> organisationsOverride) throws JEVisException {
        DemoConfig config = DemoConfig.read(service);
        Instant now = clock.instant();
        LocalDate today = now.atZone(ReplayPlanner.ZONE).toLocalDate();
        List<Long> orgIds = organisationsOverride != null ? organisationsOverride : config.organisations;
        if (orgIds.isEmpty()) {
            logger.warn("No organisation configured in '{}' of {} [{}]", DemoConfig.A_ORGANIZATION, service.getName(), service.getID());
            return;
        }
        logger.info("JEDemo cycle {}for organisations {}", dryRun ? "(dry run, nothing is written) " : "", orgIds);

        Set<Long> excluded = new HashSet<>(config.excluded);
        Map<Long, String> stateBefore = new TreeMap<>(config.replayState);

        // id -> organisation name, for the safety check of every write
        Map<Long, String> allowed = new LinkedHashMap<>();
        List<JEVisObject> objects = new ArrayList<>();
        for (Long orgId : orgIds) {
            JEVisObject org = ds.getObject(orgId);
            if (org == null) {
                logger.error("Organisation {} not found or not readable, skipped", orgId);
                continue;
            }
            if (!DemoConfig.isDemoName(org.getName())) {
                logger.error("Refusing organisation '{}' [{}]: the name does not contain \"Demo\". Nothing below it is touched.",
                        org.getName(), orgId);
                continue;
            }
            for (JEVisObject o : subtree(org, excluded)) {
                if (!allowed.containsKey(o.getID())) {
                    allowed.put(o.getID(), org.getName());
                    objects.add(o);
                }
            }
        }
        if (objects.isEmpty()) return;

        replay(objects, config, now);
        rollDates(service, objects, config, today);
        invoices(config, allowed, now);

        if (!config.replayState.equals(stateBefore)) {
            String json = DemoConfig.formatState(config.replayState);
            if (dryRun) {
                logger.info("[dry run] would store {}: {}", DemoConfig.A_REPLAY_STATE, json);
            } else {
                write(service, DemoConfig.A_REPLAY_STATE, json, now);
            }
        }
    }

    /**
     * All objects below the organisation (including it), without links and without excluded objects and their
     * children.
     */
    private List<JEVisObject> subtree(JEVisObject root, Set<Long> excluded) throws JEVisException {
        List<JEVisObject> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<JEVisObject> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            JEVisObject o = queue.poll();
            if (!seen.add(o.getID()) || excluded.contains(o.getID())) continue;
            if (LINK_CLASS.equals(o.getJEVisClassName())) continue;
            result.add(o);
            queue.addAll(o.getChildren());
        }
        return result;
    }

    // ---------------------------------------------------------------------------------------------------- replay

    private void replay(List<JEVisObject> objects, DemoConfig config, Instant now) {
        Set<Long> written = writtenByOthers(objects);
        int rows = 0, copied = 0, skipped = 0, upToDate = 0, failed = 0;
        for (JEVisObject o : objects) {
            try {
                if (!ROW_CLASSES.contains(o.getJEVisClassName())) continue;
                if (written.contains(o.getID())) {
                    skipped++;
                    logger.debug("Row {} [{}] is written by a calculation or data source, skipped", o.getName(), o.getID());
                    continue;
                }
                rows++;
                int n = replayRow(o, config, now);
                if (n < 0) skipped++;
                else if (n == 0) upToDate++;
                else copied += n;
            } catch (Exception e) {
                failed++;
                logger.error("Replay of {} [{}] failed", o.getName(), o.getID(), e);
            }
        }
        logger.info("Replay: {} rows checked, {} samples {}, {} rows up to date, {} skipped, {} failed",
                rows, copied, dryRun ? "would be written" : "written", upToDate, skipped, failed);
    }

    /**
     * Rows written by JECalc (calculation outputs) or the data collector (channel / data point targets).
     */
    private Set<Long> writtenByOthers(List<JEVisObject> objects) {
        Set<Long> ids = new HashSet<>();
        for (JEVisObject o : objects) {
            try {
                String className = o.getJEVisClassName();
                List<String> attributes;
                if (CALC_OUTPUT_CLASS.equals(className)) {
                    attributes = Collections.singletonList(CALC_OUTPUT_ATTRIBUTE);
                } else if (className != null && (className.endsWith("Channel") || className.endsWith("Data Point")
                        || className.equals("SQL Request"))) {
                    attributes = CHANNEL_TARGET_ATTRIBUTES;
                } else {
                    continue;
                }
                for (String name : attributes) {
                    String value = latestString(o, name);
                    ids.addAll(DemoConfig.parseIds(value));
                }
            } catch (Exception e) {
                logger.warn("Could not read the target of {} [{}]", o.getName(), o.getID(), e);
            }
        }
        return ids;
    }

    /**
     * @return number of planned samples, 0 if up to date, -1 if the row is skipped
     */
    private int replayRow(JEVisObject row, DemoConfig config, Instant now) throws JEVisException {
        JEVisAttribute value = row.getAttribute("Value");
        if (value == null || !value.hasSample()) return -1;
        JEVisSample latest = value.getLatestSample();
        if (latest == null) return -1;
        DateTime firstTs = value.getTimestampOfFirstSample();
        DateTime lastTs = latest.getTimestamp();
        if (firstTs == null || lastTs == null) return -1;
        Instant first = instant(firstTs);
        Instant last = instant(lastTs);
        if (!last.isAfter(first)) {
            logger.debug("Row {} [{}] has a single sample (constant), skipped", row.getName(), row.getID());
            return -1;
        }

        Offset offset = ReplayPlanner.Offset.parse(config.replayState.get(row.getID()));
        if (offset == null) {
            boolean calendar = isCalendar(row, value, last);
            offset = ReplayPlanner.offset(first, last, calendar);
            if (offset == null) {
                logger.debug("Row {} [{}] has less than one {} of history, skipped", row.getName(), row.getID(),
                        calendar ? "month" : "week");
                return -1;
            }
            if (!ReplayPlanner.isFullYear(offset)) {
                // short histories keep the offset they had when the job first saw them, otherwise the offset
                // would grow with every copy and the beginning of the history would be copied again and again
                config.replayState.put(row.getID(), offset.toString());
            }
        }

        Instant targetEnd = ReplayPlanner.targetEnd(last, now, offset);
        if (!targetEnd.isAfter(last)) return 0;

        boolean[] counter = {isCounter(row)};
        List<Sample> planned = plan(value, last, latest.getValue(), offset, targetEnd, counter);
        if (planned.isEmpty() && targetEnd.isBefore(now)) {
            // nothing within the catch-up limit after the last sample (sparse row or gap in the source):
            // look up to now, the planner still limits the copies from the first one on
            planned = plan(value, last, latest.getValue(), offset, now, counter);
        }
        if (planned.isEmpty()) return 0;

        String what = String.format("%s [%d]: %d samples %s .. %s (offset %s%s)", row.getName(), row.getID(),
                planned.size(), planned.get(0).getTimestamp(), planned.get(planned.size() - 1).getTimestamp(),
                offset, counter[0] ? ", counter" : "");
        if (dryRun) {
            logger.info("[dry run] would write {}", what);
            return planned.size();
        }
        for (int from = 0; from < planned.size(); from += WRITE_CHUNK) {
            List<JEVisSample> chunk = new ArrayList<>();
            for (Sample s : planned.subList(from, Math.min(planned.size(), from + WRITE_CHUNK))) {
                chunk.add(value.buildSample(new DateTime(s.getTimestamp().toEpochMilli()), s.getValue()));
            }
            value.addSamples(chunk);
        }
        logger.info("Wrote {}", what);
        return planned.size();
    }

    /**
     * @param counter in: the Clean Data setting; out: false if the source values are not a counter reading after
     *                all (some demo rows hold interval values although their Clean Data converts to differential)
     */
    private static List<Sample> plan(JEVisAttribute value, Instant last, Object lastValue, Offset offset,
                                     Instant end, boolean[] counter) throws JEVisException {
        ReplayPlanner.Range range = ReplayPlanner.sourceRange(last, end, offset, counter[0]);
        List<Sample> source = new ArrayList<>();
        for (JEVisSample s : value.getSamples(new DateTime(range.getFrom().toEpochMilli()), new DateTime(range.getTo().toEpochMilli()))) {
            source.add(new Sample(instant(s.getTimestamp()), s.getValue()));
        }
        if (counter[0] && !ReplayPlanner.looksLikeCounter(source)) counter[0] = false;
        return ReplayPlanner.plan(source, last, lastValue, offset, end, counter[0]);
    }

    private boolean isCalendar(JEVisObject row, JEVisAttribute value, Instant last) throws JEVisException {
        String period = latestString(row, "Period");
        Boolean byPeriod = ReplayPlanner.calendarFromPeriod(period);
        if (byPeriod != null) return byPeriod;
        List<Instant> recent = timestamps(value, last.minus(Duration.ofDays(10)), last);
        if (recent.size() < 3) recent = timestamps(value, last.minus(Duration.ofDays(400)), last);
        return ReplayPlanner.isCalendarPeriod(null, recent);
    }

    private static List<Instant> timestamps(JEVisAttribute value, Instant from, Instant to) throws JEVisException {
        List<Instant> list = new ArrayList<>();
        for (JEVisSample s : value.getSamples(new DateTime(from.toEpochMilli()), new DateTime(to.toEpochMilli()))) {
            list.add(instant(s.getTimestamp()));
        }
        return list;
    }

    /**
     * A row is a counter if one of its Clean Data children converts to differential values (latest setting).
     */
    private boolean isCounter(JEVisObject row) throws JEVisException {
        for (JEVisObject child : row.getChildren()) {
            if (!CLEAN_DATA.equals(child.getJEVisClassName())) continue;
            JEVisAttribute att = child.getAttribute(CONVERSION_TO_DIFFERENTIAL);
            JEVisSample s = att != null ? att.getLatestSample() : null;
            if (s != null && Boolean.TRUE.equals(s.getValueAsBoolean())) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ date rolling

    private void rollDates(JEVisObject service, List<JEVisObject> objects, DemoConfig config, LocalDate today) {
        if (config.shiftedUntil == null) {
            logger.warn("'{}' is not set, module dates are not rolled", DemoConfig.A_SHIFTED_UNTIL);
            return;
        }
        int weeks = DateShifter.weeksToShift(config.shiftedUntil, today);
        if (weeks < 1) {
            logger.info("Module dates: shifted until {}, nothing to do", config.shiftedUntil);
            return;
        }
        logger.info("Module dates: shifting by {} week(s) (shifted until {})", weeks, config.shiftedUntil);
        int shifted = 0, failed = 0;
        for (JEVisObject o : objects) {
            try {
                String className = o.getJEVisClassName();
                boolean done;
                switch (className) {
                    case "Appointment":
                        done = shiftLocalEntry(o, DateShifter.APPOINTMENT_FIELDS, weeks);
                        break;
                    case "Maintenance Message":
                        done = shiftLocalEntry(o, DateShifter.MAINTENANCE_FIELDS, weeks);
                        break;
                    case "Action":
                    case "Nonconformity":
                        done = shiftFileEntry(o, DateShifter.ACTION_FIELDS, weeks);
                        break;
                    case "Obligation":
                        done = shiftFileEntry(o, DateShifter.OBLIGATION_FIELDS, weeks);
                        break;
                    case NOTES_CLASS:
                        done = shiftNotes(o, weeks);
                        break;
                    default:
                        continue;
                }
                if (done) shifted++;
            } catch (Exception e) {
                failed++;
                logger.error("Could not shift the dates of {} [{}]", o.getName(), o.getID(), e);
            }
        }

        LocalDate until = config.shiftedUntil.plusWeeks(weeks);
        logger.info("Module dates: {} entries {}, {} failed; '{}' {} {}", shifted, dryRun ? "would be shifted" : "shifted",
                failed, DemoConfig.A_SHIFTED_UNTIL, dryRun ? "would be" : "set to", until);
        if (!dryRun) {
            // also after single failures, so the entries that were shifted are not shifted twice
            try {
                write(service, DemoConfig.A_SHIFTED_UNTIL, DemoConfig.formatDate(until), clock.instant());
            } catch (JEVisException e) {
                logger.error("Could not store '{}'", DemoConfig.A_SHIFTED_UNTIL, e);
            }
        }
    }

    /**
     * Appointments and maintenance messages: string JSON in "Data" with local date-times.
     */
    private boolean shiftLocalEntry(JEVisObject o, List<String> fields, int weeks) throws Exception {
        String json = latestString(o, "Data");
        if (json == null || json.trim().isEmpty()) return false;
        if (DateShifter.hasRecipients(json)) {
            logger.warn("{} [{}] has mail recipients, its dates are not shifted (no reminder mails from the demo)",
                    o.getName(), o.getID());
            return false;
        }
        String shifted = DateShifter.shiftLocalFields(json, fields, weeks);
        if (shifted == null) return false;
        if (dryRun) {
            logger.info("[dry run] would shift {} [{}]", o.getName(), o.getID());
        } else {
            write(o, "Data", shifted, clock.instant());
            logger.debug("Shifted {} [{}]", o.getName(), o.getID());
        }
        return true;
    }

    /**
     * Actions, nonconformities, obligations: file JSON in "Data" with ISO dates; a new file sample like the
     * Control Center writes it.
     */
    private boolean shiftFileEntry(JEVisObject o, List<String> fields, int weeks) throws Exception {
        JEVisAttribute att = o.getAttribute("Data");
        JEVisSample sample = att != null ? att.getLatestSample() : null;
        JEVisFile file = sample != null ? sample.getValueAsFile() : null;
        if (file == null || file.getBytes() == null) return false;
        String shifted = DateShifter.shiftIsoFields(new String(file.getBytes(), StandardCharsets.UTF_8), fields, weeks);
        if (shifted == null) return false;
        if (dryRun) {
            logger.info("[dry run] would shift {} [{}]", o.getName(), o.getID());
            return true;
        }
        Instant now = clock.instant();
        String name = "DataModel_v2_" + FILE_STAMP.format(now.atZone(ReplayPlanner.ZONE)) + ".json";
        JEVisSample newSample = att.buildSample(new DateTime(now.toEpochMilli()),
                new JEVisFileImp(name, shifted.getBytes(StandardCharsets.UTF_8)));
        newSample.commit();
        logger.debug("Shifted {} [{}]", o.getName(), o.getID());
        return true;
    }

    /**
     * Data notes: the samples of "Value", "Tag" and "User" move by whole weeks (local time kept), so the notes stay
     * on the replayed curves. The old time stamps are deleted first and the moved samples written afterwards, so a
     * service user without the right to delete note samples changes nothing (no doubled notes).
     */
    private boolean shiftNotes(JEVisObject o, int weeks) throws JEVisException {
        boolean any = false, first = true;
        for (String name : NOTES_ATTRIBUTES) {
            JEVisAttribute att = o.getAttribute(name);
            List<JEVisSample> samples = att != null ? new ArrayList<>(att.getAllSamples()) : null;
            if (samples == null || samples.isEmpty()) continue;
            any = true;
            if (dryRun) continue;
            List<Object[]> moved = new ArrayList<>();
            Set<Long> newTimes = new HashSet<>();
            for (JEVisSample s : samples) {
                long ts = DateShifter.shiftMillis(s.getTimestamp().getMillis(), weeks);
                newTimes.add(ts);
                moved.add(new Object[]{ts, s.getValueAsString(), s.getNote()});
            }
            for (JEVisSample s : samples) {
                long ts = s.getTimestamp().getMillis();
                if (newTimes.contains(ts)) continue; // overwritten by the moved sample
                // the delete request has second precision; the range must not reach a moved sample
                long from = ts - Math.floorMod(ts, 1000L);
                long to = ts % 1000 == 0 ? from : from + 1000;
                boolean clash = newTimes.stream().anyMatch(n -> n >= from && n <= to);
                boolean deleted = !clash && att.deleteSamplesBetween(new DateTime(from), new DateTime(to));
                if (!deleted && first) {
                    throw new JEVisException("Old note samples of '" + name + "' could not be deleted (missing delete right?), notes not shifted", 403);
                }
                if (!deleted) {
                    logger.warn("Could not remove the old note sample {} of {} [{}] '{}'", s.getTimestamp(), o.getName(), o.getID(), name);
                }
                first = false;
            }
            List<JEVisSample> write = new ArrayList<>();
            for (Object[] m : moved) write.add(att.buildSample(new DateTime((long) m[0]), m[1], (String) m[2]));
            try {
                att.addSamples(write);
            } catch (JEVisException | RuntimeException e) {
                // the old samples are gone already: log the content so it can be restored by hand
                for (Object[] m : moved) logger.error("Note sample not written: {} [{}] '{}' {} = {}", o.getName(), o.getID(), name, new DateTime((long) m[0]), m[1]);
                throw e;
            }
        }
        if (any) logger.debug("{} notes of {} [{}]", dryRun ? "[dry run] would shift" : "Shifted", o.getName(), o.getID());
        return any;
    }

    // ------------------------------------------------------------------------------------------------ invoices

    private void invoices(DemoConfig config, Map<Long, String> allowed, Instant now) {
        if (config.invoiceDirectory == null) return;
        YearMonth current = YearMonth.from(now.atZone(ReplayPlanner.ZONE));
        YearMonth billed = current.minusMonths(1);
        if (billed.equals(invoicedMonth)) return;

        Long directory = config.invoiceDirectory;
        String buyer = allowed.get(directory);
        try {
            JEVisObject dir = ds.getObject(directory);
            if (dir == null || buyer == null) {
                logger.error("Invoice directory {} is not below a configured demo organisation, no invoices", directory);
                return;
            }
            if (!INVOICE_DIRECTORY_CLASS.equals(dir.getJEVisClassName())) {
                logger.error("Invoice directory {} [{}] is no '{}', no invoices", dir.getName(), directory, INVOICE_DIRECTORY_CLASS);
                return;
            }
        } catch (JEVisException e) {
            logger.error("Could not read invoice directory {}", directory, e);
            return;
        }

        boolean complete = invoice(InvoiceBuilder.Medium.ELECTRICITY, config.electricityRow, config.electricityPrice,
                config.electricityFee, billed, current, buyer, directory, now);
        complete &= invoice(InvoiceBuilder.Medium.GAS, config.gasRow, config.gasPrice, config.gasFee, billed, current,
                buyer, directory, now);
        if (complete) invoicedMonth = billed;
    }

    /**
     * @return true if the invoice is done for this month (created, already present, or not configured)
     */
    private boolean invoice(InvoiceBuilder.Medium medium, Long rowId, BigDecimal price, BigDecimal fee, YearMonth billed,
                            YearMonth current, String buyer, long directory, Instant now) {
        if (rowId == null) return true;
        String number = InvoiceBuilder.number(medium, billed);
        try {
            JEVisObject row = ds.getObject(rowId);
            JEVisAttribute value = row != null ? row.getAttribute("Value") : null;
            if (value == null) {
                logger.error("{} row {} not found, no invoice {}", medium.getLabel(), rowId, number);
                return true;
            }
            Instant start = billed.atDay(1).atStartOfDay(ReplayPlanner.ZONE).toInstant();
            Instant end = current.atDay(1).atStartOfDay(ReplayPlanner.ZONE).toInstant();
            JEVisSample latest = value.getLatestSample();
            if (latest == null || instant(latest.getTimestamp()).isBefore(end)) {
                logger.info("Invoice {}: waiting until the data of {} are complete", number, billed);
                return false;
            }
            double sum = 0;
            int count = 0;
            for (JEVisSample s : value.getSamples(new DateTime(start.toEpochMilli()), new DateTime(end.toEpochMilli()))) {
                if (!instant(s.getTimestamp()).isBefore(end)) continue;
                Double v = s.getValueAsDouble();
                if (v == null || v.isNaN() || v.isInfinite()) continue;
                sum += v;
                count++;
            }
            if (count == 0) {
                logger.warn("Invoice {}: no data in {} [{}] for {}, skipped", number, row.getName(), rowId, billed);
                return true;
            }
            InvoiceBuilder.Spec spec = new InvoiceBuilder.Spec(medium, billed, BigDecimal.valueOf(sum), price, fee, buyer,
                    now.atZone(ReplayPlanner.ZONE).toLocalDate());
            InvoiceBuilder.Amounts amounts = InvoiceBuilder.amounts(spec);
            if (dryRun) {
                logger.info("[dry run] would import invoice {}: {} kWh, {}", number, spec.getKWh(), amounts);
                return true;
            }
            if (!(ds instanceof JEVisDataSourceWS)) {
                logger.error("Invoice import needs the web service data source");
                return true;
            }
            Path dir = Files.createTempDirectory("jedemo");
            File file = dir.resolve(InvoiceBuilder.fileName(spec)).toFile();
            try {
                Files.write(file.toPath(), InvoiceBuilder.pdf(spec));
                ((JEVisDataSourceWS) ds).importInvoice(file, directory, medium.name());
                logger.info("Imported invoice {}: {} kWh, {}", number, spec.getKWh(), amounts);
            } catch (InvoiceRequestException e) {
                if (e.getHttpStatus() == 409) {
                    logger.info("Invoice {} already exists, skipped", number);
                } else {
                    logger.error("Import of invoice {} failed: HTTP {} {} {}", number, e.getHttpStatus(), e.getErrorCode(), e.getMessage());
                    return false;
                }
            } finally {
                Files.deleteIfExists(file.toPath());
                Files.deleteIfExists(dir);
            }
            return true;
        } catch (Exception e) {
            logger.error("Invoice {} failed", number, e);
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static String latestString(JEVisObject o, String attribute) throws JEVisException {
        JEVisAttribute att = o.getAttribute(attribute);
        if (att == null) return null;
        JEVisSample s = att.getLatestSample();
        return s != null ? s.getValueAsString() : null;
    }

    private static void write(JEVisObject o, String attribute, String value, Instant ts) throws JEVisException {
        JEVisAttribute att = o.getAttribute(attribute);
        if (att == null) throw new JEVisException("Attribute '" + attribute + "' missing on " + o.getID(), 404);
        att.buildSample(new DateTime(ts.toEpochMilli()), value).commit();
    }

    private static Instant instant(DateTime time) {
        return Instant.ofEpochMilli(time.getMillis());
    }
}
