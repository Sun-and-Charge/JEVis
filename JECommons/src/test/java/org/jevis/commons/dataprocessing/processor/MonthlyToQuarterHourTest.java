/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
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
package org.jevis.commons.dataprocessing.processor;

import org.jevis.api.*;
import org.jevis.commons.dataprocessing.CleanDataObject;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.dataprocessing.processor.preparation.PrepareStep;
import org.jevis.commons.dataprocessing.processor.steps.AggregationAlignmentStep;
import org.jevis.commons.dataprocessing.processor.steps.DifferentialStep;
import org.jevis.commons.dataprocessing.processor.steps.PeriodAlignmentStep;
import org.jevis.commons.dataprocessing.processor.steps.ScalingStep;
import org.jevis.commons.dataprocessing.processor.workflow.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Monthly raw values cleaned to 15-minute values (bug report 2026-11-27: "a large number of values are lost"). Runs
 * the real steps of the clean pipeline (prepare, period alignment, differential, scaling, aggregation alignment) in
 * batches like {@code ProcessManager.start()}: each batch starts at the last written clean sample, the results are
 * written like {@code ImportStep} (delete the batch range, insert the values that are not null).
 */
public class MonthlyToQuarterHourTest {

    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final DateTime EPOCH = new DateTime(2000, 1, 1, 0, 0, DateTimeZone.UTC);
    private DateTimeZone previousDefault;

    @Before
    public void zone() {
        previousDefault = DateTimeZone.getDefault();
        DateTimeZone.setDefault(ZONE);
    }

    @After
    public void restore() {
        DateTimeZone.setDefault(previousDefault);
    }

    /** consumption of month i (0 = Jan 2024) */
    private static double consumption(int i) {
        return 1000 + 100 * i;
    }

    private static DateTime month(int i) {
        return new DateTime(2024, 1, 1, 0, 0, ZONE).plusMonths(i);
    }

    @Test
    public void monthlyConsumptionValuesKeepTheirSum() throws Exception {
        List<JEVisSample> raw = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < 24; i++) {
            raw.add(new VirtualSample(month(i).withZone(DateTimeZone.UTC), consumption(i)));
            total += consumption(i);
        }
        check(raw, false, total, 50000);
    }

    @Test
    public void monthlyConsumptionValuesKeepTheirSumWithSmallBatches() throws Exception {
        List<JEVisSample> raw = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < 24; i++) {
            raw.add(new VirtualSample(month(i).withZone(DateTimeZone.UTC), consumption(i)));
            total += consumption(i);
        }
        check(raw, false, total, 10000);
    }

    /* open (2026-11-27): monthly counter readings to 15 minutes give zeros for two months before the first reading
       and lose one value per month (17.5 of 51600 here); part of the DataProcessor test matrix in the plan */
    @Ignore("monthly counter readings: zeros before the first reading, one value per month lost")
    @Test
    public void monthlyCounterReadingsKeepTheirSum() throws Exception {
        List<JEVisSample> raw = new ArrayList<>();
        double reading = 50000;
        for (int i = 0; i <= 24; i++) {
            raw.add(new VirtualSample(month(i).withZone(DateTimeZone.UTC), reading));
            if (i < 24) reading += consumption(i);
        }
        check(raw, true, reading - 50000, 50000);
    }

    private void check(List<JEVisSample> raw, boolean differential, double expectedTotal, int batchSize) throws Exception {
        TreeMap<DateTime, Double> clean = new TreeMap<>();
        int batches = 0;
        boolean finished = false;
        while (!finished && batches < 20) {
            batches++;
            finished = runBatch(raw, differential, clean, batchSize);
        }

        DateTime first = clean.firstKey().withZone(ZONE);
        DateTime last = clean.lastKey().withZone(ZONE);
        double sum = 0;
        for (double v : clean.values()) sum += v;
        int quarterHours = 0;
        for (DateTime t = new DateTime(2024, 1, 1, 0, 0, ZONE); t.isBefore(new DateTime(2026, 1, 1, 0, 0, ZONE)); t = t.plusMinutes(15)) {
            quarterHours++;
        }
        String summary = String.format("batches %d, values %d (expected %d), %s .. %s, sum %.1f (expected %.1f)",
                batches, clean.size(), quarterHours, first, last, sum, expectedTotal);
        System.out.println((differential ? "counter: " : "consumption: ") + summary);
        Map<String, Double> perMonth = new TreeMap<>();
        Map<String, Integer> countPerMonth = new TreeMap<>();
        for (Map.Entry<DateTime, Double> e : clean.entrySet()) {
            String key = e.getKey().withZone(ZONE).minusMinutes(1).toString("yyyy-MM");
            perMonth.merge(key, e.getValue(), Double::sum);
            countPerMonth.merge(key, 1, Integer::sum);
        }
        for (String key : perMonth.keySet()) {
            System.out.println("  " + key + ": " + countPerMonth.get(key) + " values, sum " + String.format("%.1f", perMonth.get(key)));
        }
        assertEquals(summary, quarterHours, clean.size());
        assertEquals(summary, expectedTotal, sum, 0.5);
    }

    /** @return true when the batch reports finished */
    private boolean runBatch(List<JEVisSample> raw, boolean differential, TreeMap<DateTime, Double> clean, int batchSize) throws Exception {
        DateTime firstDate = clean.isEmpty()
                ? new DateTime(raw.get(0).getTimestamp().getYear(), raw.get(0).getTimestamp().getMonthOfYear(), raw.get(0).getTimestamp().getDayOfMonth(), 0, 0)
                : clean.lastKey();

        JEVisDataSource ds = mock(JEVisDataSource.class);
        JEVisObject cleanObject = mock(JEVisObject.class);
        when(cleanObject.getDataSource()).thenReturn(ds);
        when(cleanObject.getID()).thenReturn(4711L);
        when(cleanObject.getName()).thenReturn("Bereinigte Daten");

        JEVisAttribute rawAttribute = mock(JEVisAttribute.class);
        when(rawAttribute.getTimestampOfFirstSample()).thenReturn(raw.get(0).getTimestamp());
        when(rawAttribute.getSamples(any(DateTime.class), any(DateTime.class))).thenAnswer(inv -> {
            DateTime from = inv.getArgument(0);
            DateTime to = inv.getArgument(1);
            List<JEVisSample> list = new ArrayList<>();
            for (JEVisSample s : raw) {
                if (!s.getTimestamp().isBefore(from) && !s.getTimestamp().isAfter(to)) list.add(copy(s));
            }
            return list;
        });

        JEVisObject rawObject = mock(JEVisObject.class);
        JEVisAttribute periodAttribute = mock(JEVisAttribute.class);
        JEVisSample periodSample = mock(JEVisSample.class);
        when(periodSample.getTimestamp()).thenReturn(EPOCH);
        when(periodSample.getValueAsString()).thenReturn("P1M");
        when(periodAttribute.getAllSamples()).thenReturn(Collections.singletonList(periodSample));
        when(rawObject.getAttribute("Period")).thenReturn(periodAttribute);

        /* raw window as CleanDataObject.getRawSamplesDown: from four max periods (months) before the first date */
        List<JEVisSample> window = new ArrayList<>();
        DateTime windowStart = firstDate.minusMonths(5);
        for (JEVisSample s : raw) {
            if (!s.getTimestamp().isBefore(windowStart)) window.add(copy(s));
        }

        List<PeriodRule> rawPeriods = Collections.singletonList(new PeriodRule(EPOCH, Period.months(1)));
        List<PeriodRule> cleanPeriods = Collections.singletonList(new PeriodRule(EPOCH, Period.minutes(15)));
        DateTime maxEnd = window.get(window.size() - 1).getTimestamp().plus(Period.minutes(15));

        CleanDataObject cdo = mock(CleanDataObject.class);
        when(cdo.getCleanObject()).thenReturn(cleanObject);
        when(cdo.getRawDataObject()).thenReturn(rawObject);
        when(cdo.getRawAttribute()).thenReturn(rawAttribute);
        when(cdo.getRawSamplesDown()).thenReturn(window);
        when(cdo.getNotesMap()).thenReturn(new HashMap<>());
        when(cdo.getUserDataMap()).thenReturn(new HashMap<>());
        when(cdo.getCleanDataPeriodAlignment()).thenReturn(cleanPeriods);
        when(cdo.getRawDataPeriodAlignment()).thenReturn(rawPeriods);
        when(cdo.getIsPeriodAligned()).thenReturn(true);
        when(cdo.getFirstDate()).thenReturn(firstDate);
        when(cdo.getMaxEndDate()).thenReturn(maxEnd);
        when(cdo.getDifferentialRules()).thenReturn(Collections.singletonList(new DifferentialRule(EPOCH, differential)));
        when(cdo.getReprocessingPeriods()).thenReturn(1);
        when(cdo.getMaxProcessingSize()).thenReturn(batchSize);
        when(cdo.getValueIsQuantity()).thenReturn(true);
        when(cdo.getOffset()).thenReturn(0d);
        when(cdo.getMultiplier()).thenReturn(new ArrayList<>());
        when(cdo.getCounterOverflow()).thenReturn(new ArrayList<>());
        when(cdo.isResetByPeriod()).thenReturn(false);
        when(cdo.getPeriodOffset()).thenReturn(0);
        when(cdo.getMaxPeriod(any())).thenReturn(Period.months(1));

        ResourceManager rm = new ResourceManager();
        rm.setCleanDataObject(cdo);
        ProcessManager pm = mock(ProcessManager.class);
        boolean[] finished = {true};
        doAnswer(inv -> {
            finished[0] = inv.getArgument(0);
            return null;
        }).when(pm).setFinished(anyBoolean());

        try {
            new PrepareStep(pm).run(rm);
        } catch (RuntimeException e) {
            /* "No new intervals": ProcessManager.reRun() ends the processing */
            return true;
        }
        new PeriodAlignmentStep().run(rm);
        new DifferentialStep().run(rm);
        new ScalingStep().run(rm);
        new AggregationAlignmentStep().run(rm);

        /* as ImportStep: delete the range of the batch, insert the values */
        List<CleanInterval> intervals = rm.getIntervals();
        DateTime from = intervals.get(0).getInterval().getStart();
        DateTime to = intervals.get(intervals.size() - 1).getInterval().getEnd();
        clean.subMap(from, true, to, true).clear();
        for (CleanInterval interval : intervals) {
            Double value = interval.getResult().getValueAsDouble();
            if (value == null || value.isNaN() || value.isInfinite()) continue;
            clean.put(interval.getResult().getTimestamp(), value);
        }
        return finished[0];
    }

    private static JEVisSample copy(JEVisSample s) throws JEVisException {
        VirtualSample v = new VirtualSample(s.getTimestamp(), s.getValueAsDouble());
        v.setNote("");
        return v;
    }
}
