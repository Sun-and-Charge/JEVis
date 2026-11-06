/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.dashboard;

import org.jevis.api.*;
import org.jevis.commons.chart.*;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.unit.JEVisUnitImp;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DashboardEvaluatorTest {
    private static final DateTimeZone BERLIN = DateTimeZone.forID("Europe/Berlin");
    private static final DateTime NOW = new DateTime(2026, 11, 6, 12, 0, BERLIN);
    private static final DateTime FIRST = new DateTime(2026, 11, 6, 0, 0, BERLIN);
    private static final double DELTA = 1e-9;

    private JEVisDataSource ds;
    private JEVisAttribute value;

    @Before
    public void setUp() throws Exception {
        ds = mock(JEVisDataSource.class);
        JEVisObject object = mock(JEVisObject.class);
        value = mock(JEVisAttribute.class);
        when(object.getID()).thenReturn(2001L);
        when(object.getName()).thenReturn("Meter");
        when(object.getJEVisClassName()).thenReturn("Data");
        when(object.getDataSource()).thenReturn(ds);
        when(object.getParents()).thenReturn(new ArrayList<>());
        when(object.getAttribute("Value")).thenReturn(value);
        JEVisAttribute period = mock(JEVisAttribute.class);
        VirtualSample periodSample = new VirtualSample(new DateTime(2020, 1, 1, 0, 0, DateTimeZone.UTC), "PT15M");
        when(period.getLatestSample()).thenReturn(periodSample);
        when(period.getAllSamples()).thenReturn(Collections.singletonList(periodSample));
        when(period.hasSample()).thenReturn(true);
        when(object.getAttribute("Period")).thenReturn(period);
        when(value.getName()).thenReturn("Value");
        when(value.getObject()).thenReturn(object);
        when(value.getDisplayUnit()).thenReturn(new JEVisUnitImp("kWh", "kWh", ""));
        List<JEVisSample> samples = new ArrayList<>();
        for (int i = 0; i < 3; i++) samples.add(new VirtualSample(FIRST.plusMinutes(15 * i), (double) (i + 1)));
        when(value.getSamples(any(DateTime.class), any(DateTime.class), anyBoolean(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    DateTime from = invocation.getArgument(0);
                    DateTime until = invocation.getArgument(1);
                    List<JEVisSample> inRange = new ArrayList<>();
                    for (JEVisSample sample : samples) {
                        if (!sample.getTimestamp().isBefore(from) && !sample.getTimestamp().isAfter(until)) inRange.add(sample);
                    }
                    return inRange;
                });
        when(value.getTimestampOfFirstSample()).thenReturn(FIRST);
        when(value.getTimestampOfLastSample()).thenReturn(FIRST.plusMinutes(30));
        when(value.getLatestSample()).thenReturn(samples.get(2));
        when(ds.getObject(2001L)).thenReturn(object);
    }

    private static WidgetDefinition widget(int uuid, String type) {
        WidgetDefinition widget = new WidgetDefinition();
        widget.setUuid(uuid);
        widget.setType(type);
        AnalysisDefinition data = new AnalysisDefinition();
        ChartDefinition chart = new ChartDefinition();
        chart.setId(0);
        RowDefinition row = new RowDefinition();
        row.setKey("0-2001-Value");
        row.setObjectId(2001);
        row.setAttribute("Value");
        row.setUnitFormula("kWh");
        row.setUnitLabel("kWh");
        row.setUnitPrefix("");
        chart.getRows().add(row);
        data.getCharts().add(chart);
        widget.setData(data);
        return widget;
    }

    private DashboardResult evaluate(DashboardDefinition definition, DashboardRequest request) {
        DashboardTimeFrames frames = new DashboardTimeFrames(BERLIN, null, null, NOW);
        return new DashboardEvaluator(ds).evaluate(definition, request.setZone(BERLIN).setNow(NOW), frames);
    }

    @Test
    public void evaluatesTheWidgetsLikeTheControlCenter() throws Exception {
        DashboardDefinition definition = new DashboardDefinition();
        definition.getWidgets().add(widget(1, "Value"));
        definition.getWidgets().add(widget(2, "Chart"));
        definition.getWidgets().add(widget(3, "String Value"));
        definition.getWidgets().add(widget(4, "Toggle Switch"));
        definition.getWidgets().add(widget(5, "Title"));
        WidgetDefinition lastValue = widget(6, "Value");
        lastValue.setTimeFrame("LASTVALUE");
        definition.getWidgets().add(lastValue);
        WidgetDefinition alarms = new WidgetDefinition();
        alarms.setUuid(7);
        alarms.setType("Alarm Count");
        definition.getWidgets().add(alarms);
        definition.getWidgets().add(widget(8, "Future Widget"));

        DashboardResult result = evaluate(definition, new DashboardRequest().setTimeFrame("P1D"));
        assertEquals("P1D", result.getTimeFrame());
        assertEquals(FIRST.getMillis(), result.getFrom().getMillis());
        assertEquals(NOW.minusDays(1).getMillis(), result.getPrevious().getMillis());
        assertNull(result.getNext());
        assertFalse(result.getWidgets().containsKey("5"));
        assertFalse(result.getWidgets().containsKey("8"));

        RowResult valueRow = result.getWidgets().get("1").getRows().get(0);
        assertEquals(RowStatus.OK, valueRow.getStatus());
        assertEquals(6d, (Double) valueRow.getValue(), DELTA);
        assertEquals(Integer.valueOf(3), valueRow.getCount());
        assertEquals(FIRST.plusMinutes(30).getMillis(), valueRow.getLastTimestamp().getMillis());

        DashboardResult.WidgetResult chart = result.getWidgets().get("2");
        assertEquals(DashboardResult.WidgetResult.Kind.SERIES, chart.getKind());
        assertEquals(3, chart.getRows().get(0).getSamples().size());
        assertNotNull(chart.getRows().get(0).getStats());

        assertEquals("6.0", result.getWidgets().get("3").getRows().get(0).getText());
        assertEquals(3d, (Double) result.getWidgets().get("4").getRows().get(0).getValue(), DELTA);

        /* a fixed time frame of zero length: the dashboard interval and the newest sample */
        DashboardResult.WidgetResult last = result.getWidgets().get("6");
        assertEquals(FIRST.getMillis(), last.getFrom().getMillis());
        assertEquals(3d, (Double) last.getRows().get(0).getValue(), DELTA);
        verify(value, atLeastOnce()).getSamples(eq(FIRST.plusMinutes(30)), eq(FIRST.plusMinutes(30)), anyBoolean(),
                anyString(), anyString(), anyString());

        assertEquals(Integer.valueOf(0), result.getWidgets().get("7").getAlarmCount());
    }

    @Test
    public void unknownTimeFramesFallBackToTheDashboard() {
        DashboardDefinition definition = new DashboardDefinition();
        definition.setTimeFrame("P1W");
        DashboardResult result = evaluate(definition, new DashboardRequest().setTimeFrame("P2D"));
        assertEquals("P1W", result.getTimeFrame());
        definition.setTimeFrame("P2D");
        assertEquals("P1D", evaluate(definition, new DashboardRequest()).getTimeFrame());
    }

    @Test
    public void customNeedsFromAndTo() {
        DashboardDefinition definition = new DashboardDefinition();
        try {
            evaluate(definition, new DashboardRequest().setTimeFrame("CUSTOM"));
            fail("CUSTOM without from/to");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        DashboardResult result = evaluate(definition, new DashboardRequest().setTimeFrame("CUSTOM")
                .setFrom(FIRST.minusDays(2)).setTo(FIRST));
        assertEquals(FIRST.minusDays(2).getMillis(), result.getFrom().getMillis());
        assertNull(result.getPrevious());
        assertNull(result.getNext());
    }

    @Test
    public void automaticAggregation() {
        assertEquals(AggregationPeriod.NONE, DashboardEvaluator.autoAggregation(new TimeRange(NOW.minusDays(5), NOW)));
        assertEquals(AggregationPeriod.HOURLY, DashboardEvaluator.autoAggregation(new TimeRange(NOW.minusDays(7), NOW)));
        assertEquals(AggregationPeriod.DAILY, DashboardEvaluator.autoAggregation(new TimeRange(NOW.minusDays(30), NOW)));
        assertEquals(AggregationPeriod.WEEKLY, DashboardEvaluator.autoAggregation(new TimeRange(NOW.minusDays(365), NOW)));

        Period quarter = Period.minutes(15);
        /* chart widgets: rows without own aggregation get the automatic one */
        assertEquals(AggregationPeriod.DAILY, DashboardEvaluator.chooseAggregation(AggregationPeriod.NONE, AggregationPeriod.DAILY,
                quarter, true, AnalysisChartType.LINE, AnalysisChartType.LINE, false));
        /* not for other widgets, heat maps and rate units */
        assertEquals(AggregationPeriod.NONE, DashboardEvaluator.chooseAggregation(AggregationPeriod.NONE, AggregationPeriod.DAILY,
                quarter, false, AnalysisChartType.LINE, AnalysisChartType.LINE, false));
        assertEquals(AggregationPeriod.NONE, DashboardEvaluator.chooseAggregation(AggregationPeriod.NONE, AggregationPeriod.DAILY,
                quarter, true, AnalysisChartType.LINE, AnalysisChartType.HEAT_MAP, false));
        assertEquals(AggregationPeriod.NONE, DashboardEvaluator.chooseAggregation(AggregationPeriod.NONE, AggregationPeriod.DAILY,
                quarter, true, AnalysisChartType.LINE, AnalysisChartType.LINE, true));
        /* the automatic aggregation is finer than the period of the row: none */
        assertEquals(AggregationPeriod.NONE, DashboardEvaluator.chooseAggregation(AggregationPeriod.MONTHLY, AggregationPeriod.NONE,
                quarter, false, AnalysisChartType.LINE, AnalysisChartType.LINE, false));
        assertEquals(AggregationPeriod.MONTHLY, DashboardEvaluator.chooseAggregation(AggregationPeriod.MONTHLY, AggregationPeriod.DAILY,
                quarter, false, AnalysisChartType.LINE, AnalysisChartType.LINE, false));
        /* unknown period: the stored aggregation */
        assertEquals(AggregationPeriod.MONTHLY, DashboardEvaluator.chooseAggregation(AggregationPeriod.MONTHLY, AggregationPeriod.NONE,
                null, false, AnalysisChartType.LINE, AnalysisChartType.LINE, false));
    }

    @Test
    public void dailyRowsAreReadForWholeDays() {
        DateTime start = new DateTime(2026, 11, 2, 6, 30, 0, 0, BERLIN);
        DateTime end = new DateTime(2026, 11, 8, 5, 59, 59, 999, BERLIN);
        TimeRange days = DashboardEvaluator.snapToDays(start, end, true, null, null);
        assertEquals(new DateTime(2026, 11, 2, 0, 0, BERLIN).getMillis(), days.getStart().getMillis());
        assertEquals(new DateTime(2026, 11, 8, 23, 59, 59, 999, BERLIN).getMillis(), days.getEnd().getMillis());
        TimeRange shifted = DashboardEvaluator.snapToDays(start, end, true, LocalTime.of(6, 0), LocalTime.of(5, 59));
        assertEquals(new DateTime(2026, 11, 3, 0, 0, BERLIN).getMillis(), shifted.getStart().getMillis());
        assertTrue(DashboardEvaluator.isDailyOrCoarser(AggregationPeriod.DAILY));
        assertFalse(DashboardEvaluator.isDailyOrCoarser(AggregationPeriod.HOURLY));
    }
}
