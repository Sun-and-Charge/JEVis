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
package org.jevis.commons.chart;

import org.jevis.api.*;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.unit.ChartUnits.ChartUnits;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jevis.commons.chart.ChartTestData.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RowDataLoaderTest {
    private static final double DELTA = 1e-9;
    private static final DateTime START = new DateTime(2026, 3, 23, 0, 0, BERLIN);
    private static final DateTime END = new DateTime(2026, 3, 29, 23, 59, 59, BERLIN);
    private static final TimeRange RANGE = range(START, END);

    private JEVisDataSource ds;
    private RowDataLoader loader;

    @Before
    public void setUp() {
        ds = dataSource();
        loader = new RowDataLoader(ds);
    }

    private static List<Double> values(RowResult result) {
        List<Double> values = new ArrayList<>();
        for (ChartSample sample : result.getSamples()) values.add((Double) sample.getValue());
        return values;
    }

    @Test
    public void factorizesToTheUnitOfTheRowAndComputesStatistics() throws Exception {
        DataFake data = data(ds, 301, "Meter", "Data", unit("Wh"), samples(START, 15, 1000, 2000, 3000));

        RowResult result = loader.load(row("k", 301, "kWh"), AnalysisChartType.LINE, RANGE, null, settings());

        assertEquals(RowStatus.OK, result.getStatus());
        assertEquals("kWh", result.getUnit());
        assertEquals(3, result.getSamples().size());
        assertEquals(1d, (Double) result.getSamples().get(0).getValue(), DELTA);
        assertEquals(2d, (Double) result.getSamples().get(1).getValue(), DELTA);
        assertEquals(3d, (Double) result.getSamples().get(2).getValue(), DELTA);
        assertEquals("note0", result.getSamples().get(0).getNote());
        assertEquals(BERLIN, result.getSamples().get(0).getTimestamp().getZone());
        verify(data.value).getSamples(START, END, true, "NONE", "NONE", "Europe/Berlin");

        RowStatistics stats = result.getStats();
        assertEquals(1d, stats.getMin(), DELTA);
        assertEquals(3d, stats.getMax(), DELTA);
        assertEquals(2d, stats.getAvg(), DELTA);
        assertEquals(6d, stats.getSum(), DELTA);
        assertEquals("kWh", stats.getSumUnit());
        assertEquals(3, stats.getCount());
        assertNull(stats.getEnpi());
    }

    @Test
    public void sourceSamplesAreNotModified() throws Exception {
        List<JEVisSample> source = samples(START, 15, 1000);
        data(ds, 302, "Meter", "Data", unit("Wh"), source);
        loader.load(row("k", 302, "kWh"), AnalysisChartType.LINE, RANGE, null, settings());
        assertEquals(1000d, source.get(0).getValueAsDouble(), DELTA);
    }

    @Test
    public void noSumForNonQuantities() throws Exception {
        data(ds, 303, "Temperature", "Data", unit("°C"), samples(START, 60, 10, 20, 45));
        RowResult result = loader.load(row("t", 303, "°C"), AnalysisChartType.LINE, RANGE, null, settings());
        assertEquals(25d, result.getStats().getAvg(), DELTA);
        assertNull(result.getStats().getSum());
        assertNull(result.getStats().getSumUnit());
    }

    @Test
    public void unreadableObjectGivesNoPermission() {
        RowResult result = loader.load(row("x", 999, "kWh"), AnalysisChartType.LINE, RANGE, null, settings());
        assertEquals(RowStatus.NO_PERMISSION, result.getStatus());
        assertEquals("x", result.getKey());
        assertTrue(result.getSamples().isEmpty());
        assertNull(result.getStats());
    }

    @Test
    public void exceptionOfTheDataSourceGivesError() throws Exception {
        when(ds.getObject(998L)).thenThrow(new JEVisException("broken", 1));
        RowResult result = loader.load(row("x", 998, "kWh"), AnalysisChartType.LINE, RANGE, null, settings());
        assertEquals(RowStatus.ERROR, result.getStatus());
        assertNotNull(result.getMessage());
    }

    @Test
    public void noSamplesGivesNoData() throws Exception {
        data(ds, 304, "Meter", "Data", unit("kWh"), new ArrayList<>());
        RowResult result = loader.load(row("k", 304, "kWh"), AnalysisChartType.LINE, RANGE, null, settings());
        assertEquals(RowStatus.NO_DATA, result.getStatus());
        assertNull(result.getStats());
    }

    @Test
    public void absoluteModeOfTheSettingsKeepsNewestTimestampAndCount() throws Exception {
        data(ds, 330, "Meter", "Data", unit("kWh"), samples(START, 15, 1, 2, 3, 4));
        RowResult result = loader.load(row("k", 330, "kWh"), null, RANGE, null, settings().setAbsolute(true));
        assertEquals(RowStatus.OK, result.getStatus());
        assertEquals(1, result.getSamples().size());
        assertEquals(10d, (Double) result.getSamples().get(0).getValue(), DELTA);
        assertEquals(START.plusMinutes(45).getMillis(), result.getLastTimestamp().getMillis());
        assertEquals(Integer.valueOf(4), result.getCount());
    }

    @Test
    public void lastSampleOnlyKeepsTheNewestSample() throws Exception {
        data(ds, 331, "Meter", "Data", unit("kWh"), samples(START, 15, 1, 2, 3));
        RowResult result = loader.load(row("k", 331, "kWh"), AnalysisChartType.LINE, RANGE, null, settings().setLastSampleOnly(true));
        assertEquals(1, result.getSamples().size());
        assertEquals(3d, (Double) result.getSamples().get(0).getValue(), DELTA);
        assertEquals(1, result.getStats().getCount());
        assertEquals(Integer.valueOf(1), result.getCount());
    }

    @Test
    public void resolvesTheDataAttribute() throws Exception {
        DataFake data = data(ds, 332, "Meter", "Data", unit("kWh"), samples(START, 15, 1));
        assertSame(data.value, loader.resolveAttribute(row("k", 332, "kWh")));
        assertNull(loader.resolveAttribute(row("k", 997, "kWh")));
    }

    @Test
    public void absoluteModeForHorizontalTables() throws Exception {
        data(ds, 305, "Meter", "Data", unit("kWh"), samples(START, 15, 1, 2, 3));
        RowResult sum = loader.load(row("k", 305, "kWh"), AnalysisChartType.TABLE_H, RANGE, null, settings());
        assertEquals(1, sum.getSamples().size());
        assertEquals(6d, (Double) sum.getSamples().get(0).getValue(), DELTA);
        assertEquals(START, sum.getSamples().get(0).getTimestamp());

        RowResult average = loader.load(row("k", 305, "kWh"), AnalysisChartType.TABLE_H, RANGE, null,
                settings().setManipulation(ManipulationMode.AVERAGE));
        assertEquals(2d, (Double) average.getSamples().get(0).getValue(), DELTA);
    }

    @Test
    public void absoluteModeForNonQuantities() throws Exception {
        data(ds, 306, "Temperature", "Data", unit("°C"), samples(START, 60, 10, 20, 30));
        RowResult mean = loader.load(row("t", 306, "°C"), AnalysisChartType.TABLE_H, RANGE, null, settings());
        assertEquals(20d, (Double) mean.getSamples().get(0).getValue(), DELTA);
        /* the Control Center divides the mean a second time (6.67), see report */
        RowResult average = loader.load(row("t", 306, "°C"), AnalysisChartType.TABLE_H, RANGE, null,
                settings().setManipulation(ManipulationMode.AVERAGE));
        assertEquals(20d, (Double) average.getSamples().get(0).getValue(), DELTA);
        RowResult median = loader.load(row("t", 306, "°C"), AnalysisChartType.TABLE_H, RANGE, null,
                settings().setManipulation(ManipulationMode.MEDIAN));
        assertEquals(20d, (Double) median.getSamples().get(0).getValue(), DELTA);

        data(ds, 307, "Cold", "Data", unit("°C"), samples(START, 60, -5, -3));
        RowResult max = loader.load(row("c", 307, "°C"), AnalysisChartType.TABLE_H, RANGE, null,
                settings().setManipulation(ManipulationMode.MAX));
        assertEquals(-3d, (Double) max.getSamples().get(0).getValue(), DELTA);
    }

    @Test
    public void aggregatedRowsOfXYChartsUseTheSumUnit() throws Exception {
        DataFake data = data(ds, 308, "Power", "Data", unit("kW"), samples(START, 1440, 2, 4));
        period(data.object, "PT15M");

        RowResult result = loader.load(row("p", 308, "kW"), AnalysisChartType.COLUMN, RANGE, null,
                settings().setAggregation(AggregationPeriod.DAILY));

        verify(data.value).getSamples(START, END, true, "DAILY", "NONE", "Europe/Berlin");
        assertEquals("kWh", result.getUnit());
        double factor = new ChartUnits().scaleValue(Period.minutes(15), "kW", Period.days(1), "kWh");
        assertEquals(2d * factor, (Double) result.getSamples().get(0).getValue(), DELTA);
        assertEquals(4d * factor, (Double) result.getSamples().get(1).getValue(), DELTA);
    }

    @Test
    public void pieChartsAreNotAggregated() throws Exception {
        DataFake data = data(ds, 309, "Meter", "Data", unit("kWh"), samples(START, 15, 1, 2, 3));
        RowResult result = loader.load(row("k", 309, "kWh"), AnalysisChartType.PIE, RANGE, null,
                settings().setAggregation(AggregationPeriod.DAILY));
        verify(data.value).getSamples(START, END, true, "NONE", "NONE", "Europe/Berlin");
        assertEquals("kWh", result.getUnit());
        assertEquals(6d, result.getPieValue(), DELTA);
    }

    @Test
    public void dayFilterInThePipeline() throws Exception {
        /* Monday .. Sunday, one value per day */
        DataFake data = data(ds, 310, "Meter", "Data", unit("kWh"), samples(START.plusHours(12), 1440, 1, 2, 3, 4, 5, 6, 7));
        period(data.object, "P1D");
        DayFilter weekend = DayFilter.of("6,7", false, "");

        RowResult result = loader.load(row("k", 310, "kWh"), AnalysisChartType.LINE, RANGE, weekend, settings());
        assertEquals(Arrays.asList(1d, 2d, 3d, 4d, 5d), values(result));

        /* not applied to data aggregated to more than one day */
        RowResult weekly = loader.load(row("k", 310, "kWh"), AnalysisChartType.LINE, RANGE, weekend,
                settings().setAggregation(AggregationPeriod.WEEKLY));
        assertEquals(7, weekly.getSamples().size());
    }

    @Test
    public void stringDataRows() throws Exception {
        List<JEVisSample> texts = new ArrayList<>();
        texts.add(new VirtualSample(START, "first"));
        texts.add(new VirtualSample(START.plusHours(1), "second"));
        data(ds, 311, "Log", "String Data", unit(""), texts);
        RowResult result = loader.load(row("s", 311, ""), AnalysisChartType.TABLE, RANGE, null, settings());
        assertEquals(RowStatus.OK, result.getStatus());
        assertTrue(result.isStringData());
        assertEquals("first", result.getSamples().get(0).getValue());
        assertNull(result.getStats());
    }

    @Test
    public void cleanDataRowReadsTheCleanDataAttribute() throws Exception {
        DataFake parent = data(ds, 400, "Meter", "Data", unit("kWh"), samples(START, 15, 99));
        JEVisObject clean = mock(JEVisObject.class);
        JEVisAttribute cleanValue = mock(JEVisAttribute.class);
        when(clean.getID()).thenReturn(401L);
        when(clean.getName()).thenReturn("Meter Clean");
        when(clean.getJEVisClassName()).thenReturn("Clean Data");
        when(clean.getParents()).thenReturn(Collections.singletonList(parent.object));
        when(clean.getAttribute("Value")).thenReturn(cleanValue);
        when(cleanValue.getObject()).thenReturn(clean);
        when(cleanValue.getDisplayUnit()).thenReturn(unit("kWh"));
        when(cleanValue.getSamples(any(DateTime.class), any(DateTime.class), anyBoolean(), anyString(), anyString(), anyString()))
                .thenReturn(samples(START, 15, 5, 6));
        when(ds.getObject(401L)).thenReturn(clean);

        RowResult result = loader.load(row("c", 401, "kWh"), AnalysisChartType.LINE, RANGE, null, settings());
        assertEquals(Arrays.asList(5d, 6d), values(result));
        verify(parent.value, never()).getSamples(any(DateTime.class), any(DateTime.class), anyBoolean(), anyString(), anyString(), anyString());
    }

    @Test
    public void calculationFlagWithoutCalculationUsesTheData() throws Exception {
        DataFake data = data(ds, 312, "Meter", "Data", unit("kWh"), samples(START, 1440, 1, 2));
        RowDefinition row = row("k", 312, "kWh");
        row.setCalculation(true);
        row.setCalculationId(-1);

        RowResult result = loader.load(row, AnalysisChartType.LINE, RANGE, null, settings().setAggregation(AggregationPeriod.DAILY));
        assertFalse(result.isCalculation());
        assertEquals(RowStatus.OK, result.getStatus());
        verify(data.value).getSamples(START, END, true, "DAILY", "NONE", "Europe/Berlin");
    }

    @Test
    public void rowIntervalReplacesTheTimeFrame() throws Exception {
        DataFake data = data(ds, 313, "Meter", "Data", unit("kWh"), samples(START, 15, 1));
        RowDefinition row = row("k", 313, "kWh");
        row.setIntervalEnabled(true);
        row.setIntervalStart("2026-01-01T00:00:00.000+01:00");
        row.setIntervalEnd("2026-01-31T00:00:00.000+01:00");
        loader.load(row, AnalysisChartType.LINE, RANGE, null, settings().setCustomWorkDay(false));
        verify(data.value).getSamples(new DateTime(2026, 1, 1, 0, 0, BERLIN), new DateTime(2026, 1, 31, 0, 0, BERLIN),
                false, "NONE", "NONE", "Europe/Berlin");
    }

    @Test
    public void evaluatesAnAnalysis() throws Exception {
        data(ds, 101, "Meter A", "Data", unit("kWh"), samples(START, 15, 1, 2));
        AnalysisDefinition definition = new AnalysisDefinition();
        ChartDefinition chart = new ChartDefinition();
        chart.setId(0);
        chart.setName("Chart");
        chart.getRows().add(row("0-101-Value", 101, "kWh"));
        chart.getRows().add(row("0-102-Value", 102, "kWh"));
        definition.getCharts().add(chart);

        DateTime now = new DateTime(2026, 3, 25, 12, 0, BERLIN);
        AnalysisResult result = new AnalysisEvaluator(ds).evaluate(null, definition,
                settings().setTimeFrame(TimeFrame.TODAY).setNow(now));

        assertEquals(new DateTime(2026, 3, 25, 0, 0, BERLIN), result.getFrom());
        assertEquals(now, result.getTo());
        assertEquals(1, result.getCharts().size());
        assertEquals(RowStatus.OK, result.getCharts().get(0).getRows().get(0).getStatus());
        assertEquals(RowStatus.NO_PERMISSION, result.getCharts().get(0).getRows().get(1).getStatus());
    }
}
