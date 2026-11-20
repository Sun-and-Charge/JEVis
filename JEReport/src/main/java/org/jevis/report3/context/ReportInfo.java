/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEReport.
 * <p>
 * JEReport is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEReport is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEReport. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEReport is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.report3.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisObject;
import org.jevis.commons.datetime.Period;
import org.jevis.commons.report.PeriodMode;
import org.jevis.report3.data.report.ReportProperty;
import org.jevis.report3.data.report.intervals.IntervalCalculator;
import org.jevis.report3.data.report.intervals.ReportInterval;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The template variable {@code report}: facts about the report itself for cover sheets and headers, so a copied
 * report shows its own name, site and period without changing the template.
 * <ul>
 *     <li>{@code name}: name of the report object</li>
 *     <li>{@code site}: the nearest building above the report (empty without one)</li>
 *     <li>{@code organisation}: the nearest organisation above the report</li>
 *     <li>{@code periodStart}, {@code periodEnd}: the report period as dd.MM.yyyy (end = last day)</li>
 *     <li>{@code period}: the report period as text in German, by schedule ("Oktober 2026", "2025",
 *     "3. Quartal 2026", "KW 47 2026", a day or a range)</li>
 *     <li>{@code createdAt}: creation time as dd.MM.yyyy HH:mm</li>
 * </ul>
 */
public class ReportInfo {

    public static final String VARIABLE = "report";
    private static final Logger logger = LogManager.getLogger(ReportInfo.class);
    private static final Locale GERMAN = Locale.GERMANY;
    private static final String DATE = "dd.MM.yyyy";

    private ReportInfo() {
    }

    public static Map<String, Object> build(ReportProperty property, IntervalCalculator intervalCalc) {
        DateTimeZone zone = property != null && property.getTimeZone() != null ? property.getTimeZone() : DateTimeZone.getDefault();
        JEVisObject report = property != null ? property.getReportObject() : null;
        ReportInterval interval = intervalCalc != null ? intervalCalc.getInterval(PeriodMode.CURRENT.toString().toUpperCase()) : null;
        String schedule = intervalCalc != null ? intervalCalc.getSchedule() : null;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", report != null ? report.getName() : "");
        map.put("site", parentName(report, "Building"));
        map.put("organisation", parentName(report, "Organization"));
        if (interval != null && interval.getStart() != null && interval.getEnd() != null) {
            map.putAll(period(interval.getStart(), interval.getEnd(), schedule, zone));
        } else {
            map.put("periodStart", "");
            map.put("periodEnd", "");
            map.put("period", "");
        }
        map.put("createdAt", DateTime.now(zone).toString("dd.MM.yyyy HH:mm"));
        return map;
    }

    /** period texts for [start, end); end is exclusive or the last millisecond of the period */
    public static Map<String, Object> period(DateTime start, DateTime end, String schedule, DateTimeZone zone) {
        DateTime first = start.withZone(zone);
        DateTime last = end.withZone(zone).minusMillis(1);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("periodStart", first.toString(DATE));
        map.put("periodEnd", last.toString(DATE));
        map.put("period", text(first, last, schedule));
        return map;
    }

    public static String text(DateTime first, DateTime last, String schedule) {
        String range = first.toString(DATE) + " – " + last.toString(DATE);
        if (schedule == null) return range;
        Period p;
        try {
            p = Period.valueOf(schedule.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return range;
        }
        switch (p) {
            case DAILY:
                return first.toString(DATE);
            case WEEKLY:
                return "KW " + first.getWeekOfWeekyear() + " " + first.getWeekyear();
            case MONTHLY:
                return first.toString("MMMM yyyy", GERMAN);
            case QUARTERLY:
                return ((first.getMonthOfYear() - 1) / 3 + 1) + ". Quartal " + first.getYear();
            case YEARLY:
                return String.valueOf(first.getYear());
            default:
                return range;
        }
    }

    private static String parentName(JEVisObject object, String className) {
        try {
            for (JEVisObject o = object; o != null; ) {
                if (className.equals(o.getJEVisClassName())) return o.getName();
                List<JEVisObject> parents = o.getParents();
                o = parents == null || parents.isEmpty() ? null : parents.get(0);
            }
        } catch (Exception e) {
            logger.warn("Could not read the parents of the report: {}", e.getMessage());
        }
        return "";
    }
}
