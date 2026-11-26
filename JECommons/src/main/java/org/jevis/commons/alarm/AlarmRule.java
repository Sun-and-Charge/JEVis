/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.alarm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.constants.AlarmConstants;
import org.jevis.commons.datatype.scheduler.SchedulerHandler;
import org.jevis.commons.json.JsonAlarmConfig;
import org.jevis.commons.json.JsonScheduler;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One entry of a row's "Alarm Config" (docs/ALARMS-OUTAGE.md): a fixed limit (STATIC), a comparison row (DYNAMIC) or
 * a data outage rule (NO_DATA), each with its own silent, standby and validity times and an optional moving mean.
 */
public final class AlarmRule {

    public enum Kind {STATIC, DYNAMIC, NO_DATA}

    private static final Logger logger = LogManager.getLogger(AlarmRule.class);

    private Kind kind;
    private String id;
    private String name;
    private Double limit;
    /** the comparison row as stored ("id:Attribute"), DYNAMIC only */
    private String limitTarget;
    private AlarmConstants.Operator operator;
    private double tolerance;
    private Integer noDataMinutes;
    private Integer averageMinutes;
    private Predicate<DateTime> silent;
    private Predicate<DateTime> standby;
    private Predicate<DateTime> valid;

    private AlarmRule() {
    }

    /** a rule for tests and callers without stored JSON */
    public static AlarmRule of(Kind kind, Double limit, AlarmConstants.Operator operator, double tolerance) {
        AlarmRule r = new AlarmRule();
        r.kind = kind;
        r.limit = limit;
        r.operator = operator;
        r.tolerance = tolerance;
        return r;
    }

    public static AlarmRule noData(int minutes) {
        AlarmRule r = new AlarmRule();
        r.kind = Kind.NO_DATA;
        r.noDataMinutes = minutes;
        return r;
    }

    /**
     * @return the valid rules of the list in their order; an incomplete or unreadable entry is skipped and logged
     */
    public static List<AlarmRule> parse(List<JsonAlarmConfig> entries, String where) {
        List<AlarmRule> rules = new ArrayList<>();
        if (entries == null) return rules;
        for (JsonAlarmConfig e : entries) {
            try {
                AlarmRule r = parse(e);
                if (r != null) rules.add(r);
                else logger.warn("Incomplete alarm rule '{}' of {} skipped", e.getName(), where);
            } catch (Exception ex) {
                logger.error("Could not read alarm rule '{}' of {}", e != null ? e.getName() : null, where, ex);
            }
        }
        return rules;
    }

    /**
     * @return the rules of a row's "Alarm Config" (a list or a single entry); empty if there is none or it cannot be read
     */
    public static List<AlarmRule> ofRow(org.jevis.api.JEVisObject row) {
        try {
            org.jevis.api.JEVisAttribute a = row.getAttribute("Alarm Config");
            org.jevis.api.JEVisSample s = a != null ? a.getLatestSample() : null;
            String json = s != null ? s.getValueAsString() : null;
            if (json == null || json.trim().isEmpty()) return new ArrayList<>();
            List<JsonAlarmConfig> entries = new ArrayList<>();
            if (json.trim().startsWith("[")) {
                entries.addAll(java.util.Arrays.asList(org.jevis.commons.json.JsonTools.objectMapper().readValue(json, JsonAlarmConfig[].class)));
            } else {
                entries.add(org.jevis.commons.json.JsonTools.objectMapper().readValue(json, JsonAlarmConfig.class));
            }
            return parse(entries, row.getName() + ":" + row.getID());
        } catch (Exception e) {
            logger.error("Could not read the alarm rules of {}", row, e);
            return new ArrayList<>();
        }
    }

    /** @return the rule, null if it is incomplete */
    static AlarmRule parse(JsonAlarmConfig e) throws Exception {
        if (e == null) return null;
        AlarmRule r = new AlarmRule();
        r.id = e.getId();
        r.name = e.getName();
        if (notBlank(e.getNoData())) {
            r.kind = Kind.NO_DATA;
            r.noDataMinutes = (int) Math.round(Double.parseDouble(e.getNoData().trim()));
            if (r.noDataMinutes <= 0) return null;
        } else if (notBlank(e.getLimitData())) {
            r.kind = Kind.DYNAMIC;
            r.limitTarget = e.getLimitData().trim();
        } else if (notBlank(e.getLimit())) {
            r.kind = Kind.STATIC;
            r.limit = Double.parseDouble(e.getLimit().trim());
        } else {
            return null;
        }
        if (r.kind != Kind.NO_DATA) {
            if (!notBlank(e.getOperator())) return null;
            r.operator = AlarmConstants.Operator.parse(e.getOperator());
            if (r.operator == null) return null;
            r.tolerance = notBlank(e.getTolerance()) ? Double.parseDouble(e.getTolerance().trim()) : 0d;
            if (notBlank(e.getAverage())) {
                int minutes = (int) Math.round(Double.parseDouble(e.getAverage().trim()));
                if (minutes > 0) r.averageMinutes = minutes;
            }
            r.valid = schedule(e.getValidTime());
        }
        r.silent = schedule(e.getSilentTime());
        r.standby = schedule(e.getStandbyTime());
        return r;
    }

    private static Predicate<DateTime> schedule(JsonScheduler json) throws Exception {
        if (json == null || json.getRules() == null || json.getRules().isEmpty()) return null;
        UsageSchedule schedule = new UsageSchedule(SchedulerHandler.BuildScheduler(json.toString()), UsageScheduleType.SILENT);
        return schedule::contains;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    public AlarmRule withValid(Predicate<DateTime> valid) {
        this.valid = valid;
        return this;
    }

    public AlarmRule withSilent(Predicate<DateTime> silent) {
        this.silent = silent;
        return this;
    }

    public AlarmRule withStandby(Predicate<DateTime> standby) {
        this.standby = standby;
        return this;
    }

    public AlarmRule withAverage(Integer minutes) {
        this.averageMinutes = minutes;
        return this;
    }

    public AlarmRule withName(String name) {
        this.name = name;
        return this;
    }

    public Kind getKind() {
        return kind;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Double getLimit() {
        return limit;
    }

    public String getLimitTarget() {
        return limitTarget;
    }

    public AlarmConstants.Operator getOperator() {
        return operator;
    }

    public double getTolerance() {
        return tolerance;
    }

    public Integer getNoDataMinutes() {
        return noDataMinutes;
    }

    public Integer getAverageMinutes() {
        return averageMinutes;
    }

    public Predicate<DateTime> getSilent() {
        return silent;
    }

    public Predicate<DateTime> getStandby() {
        return standby;
    }

    public Predicate<DateTime> getValid() {
        return valid;
    }
}
