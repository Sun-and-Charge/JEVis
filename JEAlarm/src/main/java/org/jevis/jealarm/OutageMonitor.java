/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEAlarm.
 * <p>
 * JEAlarm is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEAlarm is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEAlarm. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEAlarm is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jealarm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.alarm.*;
import org.jevis.commons.json.JsonAlarm;
import org.jevis.commons.object.plugin.TargetHelper;
import org.jevis.commons.utils.CommonMethods;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.*;

/**
 * Data outage check and daily summary of one alarm configuration (docs/ALARMS-OUTAGE.md), run in every JEAlarm cycle
 * independent of the alarm period:
 * <ul>
 *     <li>rows with a "Datenausfall" rule: no new raw value for the rule's minutes → outage starts; any newer value
 *     ends it (late delivery when values inside the outage arrived, else a gap);</li>
 *     <li>data sources: newest "Last Readout" of the channels older than the source's "Latest reported" hours →
 *     transmission fault; it is named as cause of row outages whose channel belongs to it;</li>
 *     <li>one mail per run with the outages that started; the daily summary at the configured time.</li>
 * </ul>
 */
final class OutageMonitor {

    static final String ATTRIBUTE_DAILY_SUMMARY = "Daily Summary";
    static final String ATTRIBUTE_DAILY_SUMMARY_TIME = "Daily Summary Time";
    static final String KIND_ROW = "ROW";
    static final String KIND_SOURCE = "SOURCE";
    static final int DEFAULT_SOURCE_HOURS = 24;

    private static final Logger logger = LogManager.getLogger(OutageMonitor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** sends a mail with the configuration's notification; false if it has none */
    interface Mailer {
        boolean send(String subject, String html);
    }

    private final JEVisDataSource ds;
    private final AlarmConfiguration configuration;
    private final Mailer mailer;

    OutageMonitor(JEVisDataSource ds, AlarmConfiguration configuration, Mailer mailer) {
        this.ds = ds;
        this.configuration = configuration;
        this.mailer = mailer;
    }

    /**
     * @param rows    the enabled clean data rows of the configuration
     * @param sources the data sources of the configuration
     * @return ids of the rows in outage after this run (they do not hold back the period check of the others)
     */
    Set<Long> run(List<JEVisObject> rows, List<JEVisObject> sources, DateTime now) {
        Set<Long> rowsInOutage = new HashSet<>();
        JEVisObject cfg = configuration.getObject();
        try {
            JEVisAttribute stateAttribute = cfg.getAttribute(OutageState.ATTRIBUTE);
            JEVisAttribute logAttribute = cfg.getAttribute(OutageEvent.ATTRIBUTE);
            if (stateAttribute == null || logAttribute == null) {
                logger.debug("Alarm configuration {} has no outage attributes (class not updated), outage check skipped", cfg.getID());
                return rowsInOutage;
            }
            OutageState state = OutageState.fromJson(latest(stateAttribute));
            String before = state.toJson();
            List<OutageEvent> events = new ArrayList<>();
            List<OutageState.Open> started = new ArrayList<>();
            Set<String> checked = new HashSet<>();

            /* data sources first, so row outages can name them as cause */
            Map<Long, Long> sourceOfRaw = new HashMap<>();
            for (JEVisObject source : sources) {
                try {
                    if (!enabled(source)) continue;
                    String key = OutageState.sourceKey(source.getID());
                    checked.add(key);
                    List<JEVisObject> channels = new ArrayList<>();
                    collect(source, "Channel", channels);
                    DateTime newest = null;
                    for (JEVisObject channel : channels) {
                        DateTime readout = readout(channel);
                        if (readout != null && (newest == null || readout.isAfter(newest))) newest = readout;
                        for (Long raw : targets(channel)) sourceOfRaw.put(raw, source.getID());
                    }
                    if (channels.isEmpty()) continue;
                    int minutes = latestReportedHours(source) * 60;
                    apply(state, key, KIND_SOURCE, source.getID(), newest, minutes, null, now, events, started, null);
                } catch (Exception e) {
                    logger.error("Could not check data source {}:{}", source.getName(), source.getID(), e);
                }
            }

            for (JEVisObject row : rows) {
                try {
                    Integer minutes = null;
                    for (AlarmRule rule : AlarmRule.ofRow(row)) {
                        if (rule.getKind() == AlarmRule.Kind.NO_DATA && (minutes == null || rule.getNoDataMinutes() < minutes)) {
                            minutes = rule.getNoDataMinutes();
                        }
                    }
                    if (minutes == null) continue;
                    String key = OutageState.rowKey(row.getID());
                    checked.add(key);
                    JEVisObject raw = CommonMethods.getFirstParentalDataObject(row);
                    JEVisAttribute value = (raw != null ? raw : row).getAttribute("Value");
                    DateTime newest = value != null ? value.getTimestampOfLastSample() : null;
                    Long source = raw != null ? sourceOfRaw.get(raw.getID()) : null;
                    Long cause = source != null && state.getOpen().containsKey(OutageState.sourceKey(source)) ? source : null;
                    apply(state, key, KIND_ROW, row.getID(), newest, minutes, cause, now, events, started, value);
                    if (state.getOpen().containsKey(key)) rowsInOutage.add(row.getID());
                } catch (Exception e) {
                    logger.error("Could not check row {}:{} for data outage", row.getName(), row.getID(), e);
                }
            }

            /* outages of objects no longer checked (rule removed, row or source out of scope) end without a value */
            for (Iterator<Map.Entry<String, OutageState.Open>> it = state.getOpen().entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, OutageState.Open> e = it.next();
                if (!checked.contains(e.getKey())) {
                    events.add(OutageEvent.end(e.getValue(), null, false));
                    it.remove();
                }
            }

            if (!events.isEmpty()) {
                logAttribute.buildSample(now, MAPPER.writeValueAsString(events)).commit();
            }
            if (!started.isEmpty()) {
                boolean sent = mailer.send(OutageMails.startSubject(configuration.getName(), started.size()), OutageMails.startHtml(ds, started, zone()));
                logger.info("{} outage(s) started for alarm configuration {}, mail {}", started.size(), cfg.getID(), sent ? "sent" : "not sent");
            }
            if (!state.toJson().equals(before)) stateAttribute.buildSample(now, state.toJson()).commit();
        } catch (Exception e) {
            logger.error("Outage check of alarm configuration {} failed", cfg.getID(), e);
        }
        return rowsInOutage;
    }

    private void apply(OutageState state, String key, String kind, long object, DateTime newest, int minutes, Long cause,
                       DateTime now, List<OutageEvent> events, List<OutageState.Open> started, JEVisAttribute values) throws JEVisException {
        OutageState.Open open = state.getOpen().get(key);
        switch (OutageLogic.decide(open, newest, minutes, now)) {
            case START: {
                OutageState.Open o = new OutageState.Open(kind, object, newest != null ? newest.toString() : null, now.toString(), minutes, cause);
                state.getOpen().put(key, o);
                events.add(OutageEvent.start(o));
                started.add(o);
                break;
            }
            case END: {
                boolean backfilled = false;
                DateTime since = OutageLogic.parse(open.since);
                DateTime opened = OutageLogic.parse(open.opened);
                if (values != null && opened != null) {
                    List<DateTime> timestamps = new ArrayList<>();
                    for (JEVisSample s : values.getSamples(since != null ? since.plusMillis(1) : opened.minusMinutes(open.minutes), opened)) {
                        timestamps.add(s.getTimestamp());
                    }
                    backfilled = OutageLogic.backfilled(open, timestamps);
                }
                events.add(OutageEvent.end(open, newest != null ? newest.toString() : null, backfilled));
                state.getOpen().remove(key);
                break;
            }
            default:
                if (open != null && open.cause == null && cause != null) open.cause = cause;
        }
    }

    /**
     * Daily summary: limit alarms and outages of the last 24 hours and the outages still open, once a day after the
     * configured time; nothing is sent when there is nothing to report.
     */
    void dailySummary(DateTime now) {
        JEVisObject cfg = configuration.getObject();
        try {
            JEVisAttribute on = cfg.getAttribute(ATTRIBUTE_DAILY_SUMMARY);
            JEVisAttribute time = cfg.getAttribute(ATTRIBUTE_DAILY_SUMMARY_TIME);
            JEVisAttribute stateAttribute = cfg.getAttribute(OutageState.ATTRIBUTE);
            if (on == null || time == null || stateAttribute == null) return;
            JEVisSample onSample = on.getLatestSample();
            if (onSample == null || !Boolean.TRUE.equals(onSample.getValueAsBoolean())) return;
            OutageState state = OutageState.fromJson(latest(stateAttribute));
            if (!OutageLogic.summaryDue(latest(time), zone(), OutageLogic.parse(state.getLastSummary()), now)) return;

            DateTime from = now.minusHours(24);
            List<JsonAlarm> alarms = new ArrayList<>();
            JEVisAttribute logFile = configuration.getFileLogAttribute();
            if (logFile != null) {
                for (JEVisSample s : logFile.getSamples(from, now)) {
                    try {
                        JsonAlarm[] entries = MAPPER.readValue(s.getValueAsFile().getBytes(), JsonAlarm[].class);
                        for (JsonAlarm a : entries) {
                            if (a.getAlarmType() != null && AlarmTable.listedInMail(a.getAlarmType(), a.getLogValue() != null ? a.getLogValue() : 1)) {
                                alarms.add(a);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Could not read alarm log file {} of {}", s.getTimestamp(), cfg.getID(), e);
                    }
                }
            }
            List<OutageEvent> events = new ArrayList<>();
            JEVisAttribute logAttribute = cfg.getAttribute(OutageEvent.ATTRIBUTE);
            if (logAttribute != null) {
                for (JEVisSample s : logAttribute.getSamples(from, now)) events.addAll(events(s.getValueAsString()));
            }
            if (!alarms.isEmpty() || !events.isEmpty() || !state.getOpen().isEmpty()) {
                boolean sent = mailer.send(OutageMails.summarySubject(configuration.getName()),
                        OutageMails.summaryHtml(ds, alarms, events, new ArrayList<>(state.getOpen().values()), zone()));
                logger.info("Daily summary of alarm configuration {}: {}", cfg.getID(), sent ? "sent" : "not sent");
            }
            state.setLastSummary(now.toString());
            stateAttribute.buildSample(now, state.toJson()).commit();
        } catch (Exception e) {
            logger.error("Daily summary of alarm configuration {} failed", cfg.getID(), e);
        }
    }

    /** @return the events of one "Outage Log" sample (a JSON array; a single object is accepted as well) */
    static List<OutageEvent> events(String json) {
        List<OutageEvent> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        try {
            if (json.trim().startsWith("[")) list.addAll(MAPPER.readValue(json, new TypeReference<List<OutageEvent>>() {
            }));
            else {
                OutageEvent e = OutageEvent.fromJson(json);
                if (e != null) list.add(e);
            }
        } catch (Exception e) {
            logger.warn("Could not read outage log entry", e);
        }
        return list;
    }

    private DateTimeZone zone() {
        DateTimeZone zone = configuration.getDateTimeZone();
        return zone != null ? zone : DateTimeZone.getDefault();
    }

    private boolean enabled(JEVisObject source) {
        try {
            JEVisAttribute a = source.getAttribute("Enabled");
            JEVisSample s = a != null ? a.getLatestSample() : null;
            return s != null && Boolean.TRUE.equals(s.getValueAsBoolean());
        } catch (Exception e) {
            return false;
        }
    }

    private int latestReportedHours(JEVisObject source) {
        try {
            JEVisAttribute a = source.getAttribute("Latest reported");
            JEVisSample s = a != null ? a.getLatestSample() : null;
            if (s != null && s.getValueAsLong() != null && s.getValueAsLong() > 0) return s.getValueAsLong().intValue();
        } catch (Exception e) {
            logger.debug("No 'Latest reported' of {}", source.getID(), e);
        }
        return DEFAULT_SOURCE_HOURS;
    }

    /** newest "Last Readout" of a channel (ISO or "yyyy-MM-dd HH:mm:ss", as JEStatus reads it), null if none */
    static DateTime readout(JEVisObject channel) {
        try {
            JEVisAttribute a = channel.getAttribute("Last Readout");
            JEVisSample s = a != null ? a.getLatestSample() : null;
            if (s == null || s.getValueAsString() == null) return null;
            String v = s.getValueAsString().trim();
            try {
                return new DateTime(v);
            } catch (Exception e) {
                return org.joda.time.format.DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime(v);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** ids of the objects a channel (or its data point children) writes to ("Target ID" / "Target") */
    private static List<Long> targets(JEVisObject channel) {
        List<Long> ids = new ArrayList<>();
        Deque<JEVisObject> todo = new ArrayDeque<>(Collections.singletonList(channel));
        int guard = 0;
        while (!todo.isEmpty() && guard++ < 500) {
            JEVisObject o = todo.poll();
            for (String name : new String[]{"Target ID", "Target"}) {
                try {
                    JEVisAttribute a = o.getAttribute(name);
                    JEVisSample s = a != null ? a.getLatestSample() : null;
                    if (s != null && s.getValueAsString() != null && !s.getValueAsString().isEmpty()) {
                        TargetHelper th = new TargetHelper(o.getDataSource(), s.getValueAsString());
                        for (JEVisObject t : th.getObject()) if (t != null) ids.add(t.getID());
                    }
                } catch (Exception e) {
                    logger.debug("No target in {} of {}", name, o.getID(), e);
                }
            }
            try {
                todo.addAll(o.getChildren());
            } catch (Exception e) {
                logger.debug("No children of {}", o.getID(), e);
            }
        }
        return ids;
    }

    /** objects below {@code root} whose class is {@code className} or inherits from it */
    static void collect(JEVisObject root, String className, List<JEVisObject> out) throws JEVisException {
        for (JEVisObject child : root.getChildren()) {
            if (isA(child, className)) out.add(child);
            else collect(child, className, out);
        }
    }

    static boolean isA(JEVisObject object, String className) {
        try {
            JEVisClass c = object.getJEVisClass();
            for (int i = 0; c != null && i < 20; i++) {
                if (className.equals(c.getName())) return true;
                c = c.getInheritance();
            }
        } catch (Exception e) {
            logger.debug("No class of {}", object.getID(), e);
        }
        return false;
    }

    private String latest(JEVisAttribute attribute) {
        try {
            JEVisSample s = attribute.getLatestSample();
            return s != null ? s.getValueAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
