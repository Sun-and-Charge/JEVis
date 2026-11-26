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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.commons.constants.AlarmConstants;
import org.jevis.commons.json.JsonAlarmConfig;
import org.jevis.commons.json.JsonTools;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Rules, evaluation and outage decisions (docs/ALARMS-OUTAGE.md); neutral test data. */
public class AlarmRuleTest {

    private static final DateTimeZone Z = DateTimeZone.forID("Europe/Berlin");

    private static DateTime t(int day, int hour, int minute) {
        return new DateTime(2026, 11, day, hour, minute, Z);
    }

    private static List<AlarmRuleEvaluator.Point> quarterHours(DateTime from, double... values) {
        List<AlarmRuleEvaluator.Point> points = new ArrayList<>();
        for (int i = 0; i < values.length; i++) points.add(new AlarmRuleEvaluator.Point(from.plusMinutes(15 * i), values[i]));
        return points;
    }

    @Test
    public void allRulesOfARowAreReadAndOldReadersStayCompatible() throws Exception {
        String json = "[{\"name\":\"Tag\",\"id\":\"0\",\"limit\":\"40\",\"operator\":\"BIGGER\",\"tolerance\":\"0\"},"
                + "{\"name\":\"Nacht\",\"id\":\"0\",\"limit\":\"10\",\"operator\":\"BIGGER\"},"
                + "{\"name\":\"Ausfall\",\"noData\":\"120\"},"
                + "{\"name\":\"unvollständig\",\"limit\":\"5\"},"
                + "{\"name\":\"künftig\",\"limit\":\"1\",\"operator\":\"SMALLER\",\"someNewKey\":true}]";
        List<JsonAlarmConfig> entries = Arrays.asList(JsonTools.objectMapper().readValue(json, JsonAlarmConfig[].class));
        List<AlarmRule> rules = AlarmRule.parse(entries, "test");
        assertEquals(4, rules.size());
        assertEquals(AlarmRule.Kind.STATIC, rules.get(0).getKind());
        assertEquals(10d, rules.get(1).getLimit(), 0);
        assertEquals(AlarmRule.Kind.NO_DATA, rules.get(2).getKind());
        assertEquals(Integer.valueOf(120), rules.get(2).getNoDataMinutes());

        /* a rule without the new keys is written without them (older Control Centers reject unknown keys) */
        String written = new ObjectMapper().writeValueAsString(entries.get(0));
        assertFalse(written.contains("noData"));
        assertFalse(written.contains("validTime"));
        assertFalse(written.contains("average"));
    }

    @Test
    public void dayAndNightLimitsWithValidTime() {
        AlarmRule day = AlarmRule.of(AlarmRule.Kind.STATIC, 40d, AlarmConstants.Operator.BIGGER, 0)
                .withValid(ts -> ts.getHourOfDay() >= 6 && ts.getHourOfDay() < 20);
        AlarmRule night = AlarmRule.of(AlarmRule.Kind.STATIC, 10d, AlarmConstants.Operator.BIGGER, 0)
                .withValid(ts -> ts.getHourOfDay() < 6 || ts.getHourOfDay() >= 20);
        List<AlarmRuleEvaluator.Point> points = Arrays.asList(
                new AlarmRuleEvaluator.Point(t(26, 3, 0), 12),   // night, above 10
                new AlarmRuleEvaluator.Point(t(26, 10, 0), 30),  // day, below 40
                new AlarmRuleEvaluator.Point(t(26, 12, 0), 45),  // day, above 40
                new AlarmRuleEvaluator.Point(t(26, 22, 0), 8));  // night, below 10
        List<AlarmRuleEvaluator.Hit> d = AlarmRuleEvaluator.evaluate(day, points, t(26, 0, 0), null);
        List<AlarmRuleEvaluator.Hit> n = AlarmRuleEvaluator.evaluate(night, points, t(26, 0, 0), null);
        assertEquals(1, d.size());
        assertEquals(t(26, 12, 0), d.get(0).ts);
        assertEquals(1, n.size());
        assertEquals(t(26, 3, 0), n.get(0).ts);
        assertEquals(">", d.get(0).operator);
    }

    @Test
    public void movingAverageCorridorUsesValuesBeforeThePeriod() {
        /* corridor 10..20 on the mean of one hour (4 quarter hours) */
        AlarmRule low = AlarmRule.of(AlarmRule.Kind.STATIC, 10d, AlarmConstants.Operator.SMALLER, 0).withAverage(60);
        AlarmRule high = AlarmRule.of(AlarmRule.Kind.STATIC, 20d, AlarmConstants.Operator.BIGGER, 0).withAverage(60);
        List<AlarmRuleEvaluator.Point> points = quarterHours(t(26, 8, 0), 15, 15, 15, 15, 40, 0, 0, 0, 0);
        DateTime from = t(26, 9, 0);
        List<AlarmRuleEvaluator.Hit> h = AlarmRuleEvaluator.evaluate(high, points, from, null);
        List<AlarmRuleEvaluator.Hit> l = AlarmRuleEvaluator.evaluate(low, points, from, null);
        /* 9:00 mean of 8:15..9:00 = (15+15+15+40)/4 = 21.25 > 20; a single 40 alone would not count without the mean */
        assertEquals(1, h.size());
        assertEquals(21.25, h.get(0).value, 1e-9);
        /* 9:30 mean (40+0+0)/... -> 9:15 (15+15+40+0)/4=17.5, 9:30 (15+40+0+0)/4=13.75, 9:45 (40+0+0+0)/4=10, 10:00 0 */
        assertEquals(1, l.size());
        assertEquals(t(26, 10, 0), l.get(0).ts);
    }

    @Test
    public void silentAndStandbyPerRuleAndDynamicComparison() {
        AlarmRule dyn = AlarmRule.of(AlarmRule.Kind.DYNAMIC, null, AlarmConstants.Operator.BIGGER, 10)
                .withSilent(ts -> ts.getHourOfDay() == 1).withStandby(ts -> ts.getHourOfDay() == 2);
        List<AlarmRuleEvaluator.Point> points = Arrays.asList(
                new AlarmRuleEvaluator.Point(t(26, 0, 0), 120),
                new AlarmRuleEvaluator.Point(t(26, 1, 0), 120),
                new AlarmRuleEvaluator.Point(t(26, 2, 0), 120),
                new AlarmRuleEvaluator.Point(t(26, 3, 0), 105));
        List<AlarmRuleEvaluator.Hit> hits = AlarmRuleEvaluator.evaluate(dyn, points, t(26, 0, 0), ts -> 100d);
        /* 0:00 normal, 1:00 silent (2), 2:00 standby dropped, 3:00 105 within 100 + 10 % */
        assertEquals(2, hits.size());
        assertEquals(AlarmRuleEvaluator.LOG_NORMAL, hits.get(0).logValue);
        assertEquals(AlarmRuleEvaluator.LOG_SILENT, hits.get(1).logValue);
        assertEquals(110d, hits.get(0).setValue, 1e-9);
        assertEquals(AlarmType.DYNAMIC, hits.get(0).type);
        assertTrue(AlarmRuleEvaluator.evaluate(dyn, points, t(26, 0, 0), ts -> null).isEmpty());
    }

    @Test
    public void outageStartsOnceAndEndsWithLateDeliveryOrGap() {
        DateTime now = t(26, 12, 0);
        /* newest value 9:00, allowed 120 min -> start */
        assertEquals(OutageLogic.Decision.START, OutageLogic.decide(null, t(26, 9, 0), 120, now));
        assertEquals(OutageLogic.Decision.NONE, OutageLogic.decide(null, t(26, 11, 0), 120, now));
        assertEquals(OutageLogic.Decision.START, OutageLogic.decide(null, null, 120, now));

        OutageState.Open open = new OutageState.Open("ROW", 7, t(26, 9, 0).toString(), now.toString(), 120, null);
        /* still nothing new: no second start */
        assertEquals(OutageLogic.Decision.NONE, OutageLogic.decide(open, t(26, 9, 0), 120, now.plusHours(3)));
        /* late delivery: old values 9:15..11:45 arrive at 13:00 -> ends, backfilled */
        assertEquals(OutageLogic.Decision.END, OutageLogic.decide(open, t(26, 11, 45), 120, t(26, 13, 0)));
        assertTrue(OutageLogic.backfilled(open, Arrays.asList(t(26, 9, 15), t(26, 11, 45))));
        /* resumption without the old values: a gap remains */
        assertFalse(OutageLogic.backfilled(open, Collections.singletonList(t(26, 12, 30))));
    }

    @Test
    public void stateRoundTripAndDailySummaryOncePerDay() {
        OutageState s = new OutageState();
        s.getOpen().put(OutageState.rowKey(7), new OutageState.Open("ROW", 7, null, t(26, 12, 0).toString(), 120, 44L));
        s.setLastSummary(t(25, 7, 0).toString());
        OutageState back = OutageState.fromJson(s.toJson());
        assertEquals(Long.valueOf(44L), back.getOpen().get("row:7").cause);
        assertNull(back.getOpen().get("row:7").since);
        assertNotNull(OutageState.fromJson("not json"));

        DateTime last = OutageLogic.parse(back.getLastSummary());
        assertFalse(OutageLogic.summaryDue("07:00", Z, last, t(26, 6, 59)));
        assertTrue(OutageLogic.summaryDue("07:00", Z, last, t(26, 7, 0)));
        assertFalse(OutageLogic.summaryDue("07:00", Z, t(26, 7, 5), t(26, 18, 0)));
        assertFalse(OutageLogic.summaryDue("25:00", Z, null, t(26, 18, 0)));

        OutageEvent e = OutageEvent.end(back.getOpen().get("row:7"), t(26, 13, 0).toString(), true);
        OutageEvent eb = OutageEvent.fromJson(e.toJson());
        assertEquals(OutageEvent.END, eb.event);
        assertTrue(eb.backfilled);
        assertEquals(Long.valueOf(44L), eb.cause);
    }
}
