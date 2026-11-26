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

import org.jevis.commons.constants.AlarmConstants;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Evaluates one STATIC or DYNAMIC {@link AlarmRule} over the values of a row (pure, as JEAlarm did per sample before):
 * <ul>
 *     <li>values outside the rule's validity time are skipped (day/night limits);</li>
 *     <li>with a moving mean, the mean of the values in (t − average, t] is compared (value corridor = two rules);</li>
 *     <li>tolerance in percent around the limit (resp. the comparison value), operator as stored;</li>
 *     <li>log value 1 normal, +2 in the silent time, +4 in the standby time; standby alone (4) drops the alarm.</li>
 * </ul>
 */
public final class AlarmRuleEvaluator {

    public static final int LOG_NORMAL = 1;
    public static final int LOG_SILENT = 2;
    public static final int LOG_STANDBY = 4;

    private AlarmRuleEvaluator() {
    }

    /** a value of the row */
    public static final class Point {
        public final DateTime ts;
        public final double value;

        public Point(DateTime ts, double value) {
            this.ts = ts;
            this.value = value;
        }
    }

    /** an alarm of the rule */
    public static final class Hit {
        public final DateTime ts;
        /** the compared value (the mean with a moving mean) */
        public final double value;
        /** the limit including the tolerance */
        public final double setValue;
        public final String operator;
        public final int logValue;
        public final AlarmType type;
        public final AlarmRule rule;

        Hit(DateTime ts, double value, double setValue, String operator, int logValue, AlarmType type, AlarmRule rule) {
            this.ts = ts;
            this.value = value;
            this.setValue = setValue;
            this.operator = operator;
            this.logValue = logValue;
            this.type = type;
            this.rule = rule;
        }
    }

    /**
     * @param points  the values sorted by time; with a moving mean they may start up to {@code average} minutes
     *                before {@code from} (only for the mean)
     * @param from    first time stamp to report (inclusive)
     * @param compare DYNAMIC: the comparison value at a time stamp (null if none), ignored for STATIC
     */
    public static List<Hit> evaluate(AlarmRule rule, List<Point> points, DateTime from, Function<DateTime, Double> compare) {
        List<Hit> hits = new ArrayList<>();
        if (rule.getKind() == AlarmRule.Kind.NO_DATA || rule.getOperator() == null) return hits;
        int windowStart = 0;
        double windowSum = 0;
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            double compared = p.value;
            if (rule.getAverageMinutes() != null) {
                windowSum += p.value;
                DateTime earliest = p.ts.minusMinutes(rule.getAverageMinutes());
                while (windowStart < i && !points.get(windowStart).ts.isAfter(earliest)) {
                    windowSum -= points.get(windowStart).value;
                    windowStart++;
                }
                compared = windowSum / (i - windowStart + 1);
            }
            if (p.ts.isBefore(from)) continue;
            if (rule.getValid() != null && !rule.getValid().test(p.ts)) continue;

            Double base;
            AlarmType type;
            if (rule.getKind() == AlarmRule.Kind.DYNAMIC) {
                base = compare != null ? compare.apply(p.ts) : null;
                type = AlarmType.DYNAMIC;
            } else {
                base = rule.getLimit();
                type = AlarmType.STATIC;
            }
            if (base == null) continue;
            double diff = rule.getTolerance() != 0d ? base * (rule.getTolerance() / 100) : 0d;
            double lower = base - diff;
            double upper = base + diff;

            boolean alarm;
            boolean useUpper = true;
            switch (rule.getOperator()) {
                case BIGGER:
                    alarm = compared > upper;
                    break;
                case BIGGER_EQUALS:
                    alarm = compared >= upper;
                    break;
                case EQUALS:
                    alarm = compared == upper;
                    break;
                case NOT_EQUALS:
                    alarm = compared != upper;
                    break;
                case SMALLER:
                    alarm = compared < lower;
                    useUpper = false;
                    break;
                case SMALLER_EQUALS:
                    alarm = compared <= lower;
                    useUpper = false;
                    break;
                default:
                    alarm = false;
            }
            if (!alarm) continue;
            int log = logValue(rule, p.ts);
            if (log == LOG_STANDBY) continue;
            hits.add(new Hit(p.ts, compared, useUpper ? upper : lower, AlarmConstants.Operator.getValue(rule.getOperator()), log, type, rule));
        }
        return hits;
    }

    /** 1 normal, +2 silent, +4 standby (as {@link ScheduleService#getValueForLog}) */
    public static int logValue(AlarmRule rule, DateTime ts) {
        int res = 0;
        if (rule.getSilent() != null && rule.getSilent().test(ts)) res += LOG_SILENT;
        if (rule.getStandby() != null && rule.getStandby().test(ts)) res += LOG_STANDBY;
        return res == 0 ? LOG_NORMAL : res;
    }
}
