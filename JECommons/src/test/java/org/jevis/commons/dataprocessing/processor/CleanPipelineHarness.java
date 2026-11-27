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

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Runs the real steps of the clean pipeline (prepare, period alignment, differential, scaling, aggregation alignment)
 * in batches like {@code ProcessManager.start()}: every batch starts at the last written clean sample and ends when
 * {@code PrepareStep} reports finished or finds no new intervals; results are written like {@code ImportStep}
 * (delete the batch range, insert the values that are not null).
 */
final class CleanPipelineHarness {

    static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final DateTime EPOCH = new DateTime(2000, 1, 1, 0, 0, DateTimeZone.UTC);

    private final List<JEVisSample> raw;
    private final Period rawPeriod;
    private final Period cleanPeriod;
    private final boolean differential;
    private final boolean quantity;
    private final int batchSize;
    int batches;

    CleanPipelineHarness(List<JEVisSample> raw, Period rawPeriod, Period cleanPeriod, boolean differential, boolean quantity, int batchSize) {
        this.raw = raw;
        this.rawPeriod = rawPeriod;
        this.cleanPeriod = cleanPeriod;
        this.differential = differential;
        this.quantity = quantity;
        this.batchSize = batchSize;
    }

    /** @return the clean values by time stamp after all batches */
    TreeMap<DateTime, Double> run() throws Exception {
        TreeMap<DateTime, Double> clean = new TreeMap<>();
        boolean finished = false;
        while (!finished && batches < 200) {
            batches++;
            finished = runBatch(clean);
        }
        return clean;
    }

    private static Period max(Period a, Period b) {
        return approxMillis(a) >= approxMillis(b) ? a : b;
    }

    private static long approxMillis(Period p) {
        /* months and years have no standard duration */
        if (p.getYears() > 0) return 366L * 24 * 3600 * 1000 * p.getYears();
        if (p.getMonths() > 0) return 31L * 24 * 3600 * 1000 * p.getMonths();
        return p.toStandardDuration().getMillis();
    }

    private boolean runBatch(TreeMap<DateTime, Double> clean) throws Exception {
        DateTime first = raw.get(0).getTimestamp();
        /* as CleanDataObject.getFirstDate: the local day of the first raw value, else the last clean value */
        DateTime firstLocal = first.withZone(ZONE);
        DateTime firstDate = clean.isEmpty() ? new DateTime(firstLocal.getYear(), firstLocal.getMonthOfYear(), firstLocal.getDayOfMonth(), 0, 0, ZONE) : clean.lastKey();

        JEVisDataSource ds = mock(JEVisDataSource.class);
        JEVisObject cleanObject = mock(JEVisObject.class);
        when(cleanObject.getDataSource()).thenReturn(ds);
        when(cleanObject.getID()).thenReturn(4711L);
        when(cleanObject.getName()).thenReturn("Bereinigte Daten");

        JEVisAttribute rawAttribute = mock(JEVisAttribute.class);
        when(rawAttribute.getTimestampOfFirstSample()).thenReturn(first);
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
        when(periodSample.getValueAsString()).thenReturn(rawPeriod.toString());
        when(periodAttribute.getAllSamples()).thenReturn(Collections.singletonList(periodSample));
        when(rawObject.getAttribute("Period")).thenReturn(periodAttribute);

        /* raw window as CleanDataObject.getRawSamplesDown: from four max periods before the first date */
        long maxMillis = Math.max(approxMillis(rawPeriod), approxMillis(cleanPeriod));
        DateTime windowStart = firstDate.minus(maxMillis * 5);
        List<JEVisSample> window = new ArrayList<>();
        for (JEVisSample s : raw) {
            if (!s.getTimestamp().isBefore(windowStart)) window.add(copy(s));
        }
        if (window.isEmpty()) return true;

        List<PeriodRule> rawPeriods = Collections.singletonList(new PeriodRule(EPOCH, rawPeriod));
        List<PeriodRule> cleanPeriods = Collections.singletonList(new PeriodRule(EPOCH, cleanPeriod));
        /* as CleanDataObject.getMaxEndDate: last raw value plus one clean period, in local time */
        DateTime maxEnd = window.get(window.size() - 1).getTimestamp().withZone(ZONE).plus(cleanPeriod);

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
        when(cdo.getValueIsQuantity()).thenReturn(quantity);
        when(cdo.getOffset()).thenReturn(0d);
        when(cdo.getMultiplier()).thenReturn(new ArrayList<>());
        when(cdo.getCounterOverflow()).thenReturn(new ArrayList<>());
        when(cdo.isResetByPeriod()).thenReturn(false);
        when(cdo.getPeriodOffset()).thenReturn(0);
        when(cdo.getMaxPeriod(any())).thenReturn(max(rawPeriod, cleanPeriod));

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
            /* "No new intervals" / "No new raw data": ProcessManager.reRun() ends the processing */
            return true;
        }
        new PeriodAlignmentStep().run(rm);
        new DifferentialStep().run(rm);
        new ScalingStep().run(rm);
        new AggregationAlignmentStep().run(rm);

        List<CleanInterval> intervals = rm.getIntervals();
        if (Boolean.getBoolean("harness.debug") || System.getenv("HARNESS_ONLY") != null) {
            System.out.println("DEBUG batch " + batches + " firstDate " + firstDate.withZone(ZONE) + " maxEnd " + maxEnd.withZone(ZONE) + " intervals " + intervals.size()
                    + " raw " + window.get(0).getTimestamp().withZone(ZONE) + " .. " + window.get(window.size() - 1).getTimestamp().withZone(ZONE));
            int n = intervals.size();
            for (int k = 0; k < n; k++) {
                if (k < 4 || k >= n - 4) {
                    CleanInterval ci = intervals.get(k);
                    System.out.println("DEBUG  " + ci.getInterval().getStart().withZone(ZONE) + " .. " + ci.getInterval().getEnd().withZone(ZONE)
                            + " ts " + ci.getResult().getTimestamp().withZone(ZONE) + " raw " + ci.getRawSamples().size() + " value " + ci.getResult().getValueAsDouble());
                }
            }
        }
        if (intervals.isEmpty()) return true;
        DateTime from = intervals.get(0).getInterval().getStart();
        DateTime to = intervals.get(intervals.size() - 1).getInterval().getEnd();
        clean.subMap(from, true, to, true).clear();
        int before = clean.size();
        DateTime lastBefore = clean.isEmpty() ? null : clean.lastKey();
        for (CleanInterval interval : intervals) {
            Double value = interval.getResult().getValueAsDouble();
            if (value == null || value.isNaN() || value.isInfinite()) continue;
            clean.put(interval.getResult().getTimestamp(), value);
        }
        /* a batch that adds nothing new would repeat forever */
        if (clean.size() == before && Objects.equals(lastBefore, clean.isEmpty() ? null : clean.lastKey())) return true;
        return finished[0];
    }

    private static JEVisSample copy(JEVisSample s) throws JEVisException {
        VirtualSample v = new VirtualSample(s.getTimestamp(), s.getValueAsDouble());
        v.setNote("");
        return v;
    }
}
