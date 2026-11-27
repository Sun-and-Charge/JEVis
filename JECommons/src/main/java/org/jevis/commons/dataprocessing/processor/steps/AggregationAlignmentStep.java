/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.dataprocessing.processor.steps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisSample;
import org.jevis.commons.dataprocessing.CleanDataObject;
import org.jevis.commons.dataprocessing.processor.workflow.CleanInterval;
import org.jevis.commons.dataprocessing.processor.workflow.ProcessStep;
import org.jevis.commons.dataprocessing.processor.workflow.ResourceManager;
import org.jevis.commons.datetime.PeriodHelper;
import org.jevis.commons.datetime.WorkDays;
import org.jevis.commons.i18n.I18n;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * change aggregation of values according to setting if possible
 *
 * @author gerrit
 */
public class AggregationAlignmentStep implements ProcessStep {

    private static final Logger logger = LogManager.getLogger(AggregationAlignmentStep.class);

    @Override
    public void run(ResourceManager resourceManager) throws Exception {
        DateTime benchStart = new DateTime();
        CleanDataObject cleanDataObject = resourceManager.getCleanDataObject();

        Map<DateTime, JEVisSample> notesMap = resourceManager.getNotesMap();
        List<CleanInterval> intervals = resourceManager.getIntervals();
        List<JEVisSample> rawSamples = resourceManager.getRawSamplesDown();

        boolean downSampling = true;
        Boolean valueIsQuantity = cleanDataObject.getValueIsQuantity();

        BigDecimal offset = new BigDecimal(resourceManager.getCleanDataObject().getOffset().toString());
        List<JEVisSample> listMultipliers = resourceManager.getCleanDataObject().getMultiplier();

        WorkDays wd = new WorkDays(cleanDataObject.getCleanObject());
        DateTimeZone timeZone = wd.getDateTimeZone();

        /**
         * calc the sample per interval if possible depending on aggregation mode (avg or sum value)
         */

        List<CleanInterval> needToBeRemoved = new ArrayList<>();

        Map<DateTime, JEVisSample> userDataMap = resourceManager.getUserDataMap() != null ? resourceManager.getUserDataMap() : new java.util.HashMap<>();
        int rawIndex = -1;
        int countedIndex = -1;
        long countedPeriods = 0;
        DateTime countedEnd = null;
        double lastValue = 0d;
        for (int j = 0, intervalsSize = intervals.size(); j < intervalsSize; j++) {
            CleanInterval currentInterval = intervals.get(j);
            //align the raw samples to the intervals
            Period outputPeriod = currentInterval.getOutputPeriod();
            Period inputPeriod = currentInterval.getInputPeriod();

            int compare = currentInterval.getCompare();
            int indexOfCurrentInterval = intervals.indexOf(currentInterval);
            // if clean data period is longer (e.g. 1 day) or equal than raw data period (e.g. 15 minutes)
            // the down sampling method will be used, else the other

            if (compare < 0) {
                downSampling = false;
            }

            if (compare == 0) { //no aggregation change
                JEVisSample sample = currentInterval.getResult();
                DateTime date = sample.getTimestamp();
                String note = "";
                try {
                    note = sample.getNote();
                } catch (Exception e) {
                    note = "";
                }
                if (note == null || note.isEmpty()) {
                    note = "agg(no)";
                } else {
                    note += ",agg(no)";
                }
                if (!notesMap.isEmpty()) {
                    JEVisSample noteSample = notesMap.get(date);
                    if (noteSample != null) {
                        note += ",userNotes";
                    }
                }
                sample.setNote(note);
            } else if (downSampling && !valueIsQuantity) {

                CleanInterval lastInterval = null;
                if (indexOfCurrentInterval > 0) {
                    lastInterval = intervals.get(indexOfCurrentInterval - 1);
                }

                String note = "";
                if ((lastInterval == null || lastInterval.getRawSamples().isEmpty()) && currentInterval.isDifferential() && !currentInterval.getRawSamples().isEmpty()) {
                    if (lastInterval != null && lastInterval.getRawSamples().isEmpty()) {
                        //Should only be possible with broken timestamps, e.g. time shift for summer->winter time
                        int i = intervals.indexOf(currentInterval);
                        for (int i2 = i; i2 >= 0; i2--) {
                            CleanInterval prevInterval = intervals.get(i2);
                            if (!prevInterval.getRawSamples().isEmpty()) {
                                lastInterval = prevInterval;
                                break;
                            }
                        }
                    } else {
                        /* the reading before this interval (it may belong to an earlier batch or lie before the
                           intervals); without one the interval starts at its own first reading */
                        JEVisSample before = readingBefore(rawSamples, currentInterval);
                        if (before != null) {
                            /* an interval of its own time: CleanInterval.equals compares the date, an interval with
                               the date of the current one was taken for the current one (one raw step lost at
                               every batch start, 2026-11-27) */
                            DateTime beforeTs = before.getTimestamp();
                            lastInterval = new CleanInterval(new org.joda.time.Interval(beforeTs.minusMillis(1), beforeTs), beforeTs);
                            lastInterval.getRawSamples().add(before);
                            note += "ex-li-b";
                        } else {
                            lastInterval = new CleanInterval(currentInterval.getInterval(), currentInterval.getDate());
                            lastInterval.getRawSamples().add(currentInterval.getRawSamples().get(0));
                            note += "ex-li-m";
                        }
                    }
                }

                if (currentInterval.getRawSamples().isEmpty()) {
                    /* a mean of no values is no value (a temperature of 0 is a value) */
                    currentInterval.getResult().setValue(null);
                } else {
                    Double valueAsDouble = calcAvgSample(currentInterval.getRawSamples(), currentInterval.isDifferential(), lastInterval);
                    currentInterval.getResult().setValue(getScaledValue(listMultipliers, currentInterval.getDate(), valueAsDouble, offset).doubleValue());
                }

                try {
                    note += currentInterval.getResult().getNote();
                } catch (Exception e) {
                    note += "";
                }
                if (note.isEmpty()) {
                    note += "agg(yes," + currentInterval.getRawSamples().size() + ",avg)";
                } else {
                    note += ",agg(yes," + currentInterval.getRawSamples().size() + ",avg)";
                }
                if (!notesMap.isEmpty()) {
                    JEVisSample noteSample = notesMap.get(currentInterval.getResult().getTimestamp());
                    if (noteSample != null) {
                        note += ",userNotes";
                    }
                }
                currentInterval.getResult().setNote(note);

            } else if (downSampling) {

                CleanInterval lastInterval = null;
                if (indexOfCurrentInterval > 0) {
                    lastInterval = intervals.get(indexOfCurrentInterval - 1);
                }

                String note = "";
                if ((lastInterval == null || lastInterval.getRawSamples().isEmpty()) && currentInterval.isDifferential() && !currentInterval.getRawSamples().isEmpty()) {
                    if (lastInterval != null && lastInterval.getRawSamples().isEmpty()) {
                        //Should only be possible with broken timestamps, e.g. time shift for summer->winter time
                        int i = intervals.indexOf(currentInterval);
                        for (int i2 = i; i2 >= 0; i2--) {
                            CleanInterval prevInterval = intervals.get(i2);
                            if (!prevInterval.getRawSamples().isEmpty()) {
                                lastInterval = prevInterval;
                                break;
                            }
                        }
                    } else {
                        /* the reading before this interval (it may belong to an earlier batch or lie before the
                           intervals); without one the interval starts at its own first reading */
                        JEVisSample before = readingBefore(rawSamples, currentInterval);
                        if (before != null) {
                            /* an interval of its own time: CleanInterval.equals compares the date, an interval with
                               the date of the current one was taken for the current one (one raw step lost at
                               every batch start, 2026-11-27) */
                            DateTime beforeTs = before.getTimestamp();
                            lastInterval = new CleanInterval(new org.joda.time.Interval(beforeTs.minusMillis(1), beforeTs), beforeTs);
                            lastInterval.getRawSamples().add(before);
                            note += "ex-li-b";
                        } else {
                            lastInterval = new CleanInterval(currentInterval.getInterval(), currentInterval.getDate());
                            lastInterval.getRawSamples().add(currentInterval.getRawSamples().get(0));
                            note += "ex-li-m";
                        }
                    }
                }

                Double currentValue = 0d;

                if (!currentInterval.getRawSamples().isEmpty()) {
                    if (!currentInterval.isDifferential()) {
                        for (JEVisSample sample : currentInterval.getRawSamples()) {
                            currentValue += sample.getValueAsDouble();
                        }
                    } else {
                        Double firstValue = 0d;
                        if (!currentInterval.equals(lastInterval) && lastInterval != null && !lastInterval.getRawSamples().isEmpty()) {
                            firstValue = lastInterval.getRawSamples().get(lastInterval.getRawSamples().size() - 1).getValueAsDouble();
                        } else if (!currentInterval.getRawSamples().isEmpty()) {
                            firstValue = currentInterval.getRawSamples().get(0).getValueAsDouble();
                        }
                        currentValue = currentInterval.getRawSamples().get(currentInterval.getRawSamples().size() - 1).getValueAsDouble() - firstValue;
                    }
                }

                currentInterval.getResult().setValue(getScaledValue(listMultipliers, currentInterval.getDate(), currentValue, offset).doubleValue());

                try {
                    note += currentInterval.getRawSamples().get(currentInterval.getRawSamples().size() - 1).getNote();
                } catch (Exception e) {
                    note = "";
                }
                if (note.isEmpty()) {
                    note += "agg(yes," + currentInterval.getRawSamples().size() + ",sum)";
                } else {
                    note += ",agg(yes," + currentInterval.getRawSamples().size() + ",sum)";
                }
                if (!notesMap.isEmpty()) {
                    JEVisSample noteSample = notesMap.get(currentInterval.getResult().getTimestamp());
                    if (noteSample != null) {
                        note += ",userNotes";
                    }
                }
                currentInterval.getResult().setNote(note);
            } else if (!downSampling && !valueIsQuantity) {

                Double nextValue = null;
                int nextAdd = 0;
                Double value = null;

                if (currentInterval.getRawSamples() == null || currentInterval.getRawSamples().isEmpty()) {

                    String note = "";
                    if (j < intervals.size() - 1) {
                        for (int i = j; i < intervals.size(); i++) {
                            if (intervals.get(i).getRawSamples() != null && !intervals.get(i).getRawSamples().isEmpty()) {
                                nextValue = intervals.get(i).getResult().getValueAsDouble();
                                note = intervals.get(i).getResult().getNote();
                                nextAdd++;
                                break;
                            }
                            nextAdd++;
                        }

                    } else needToBeRemoved.add(currentInterval);

                    if (note == null || note.isEmpty()) {
                        note = "agg(yes,up," + nextAdd + ",avg)";
                    } else {
                        note = note + ",agg(yes,up," + nextAdd + ",avg)";
                    }

                    if (!notesMap.isEmpty()) {
                        JEVisSample noteSample = notesMap.get(currentInterval.getResult().getTimestamp());
                        if (noteSample != null) {
                            note += ",userNotes";
                        }
                    }

                    if (nextValue != null) {
                        value = nextValue;
                        /* the raw value stands for its own period only: intervals further back (before the first raw
                           value or in a gap) stay empty */
                        DateTime nextTs = j + nextAdd - 1 < intervalsSize ? intervals.get(j + nextAdd - 1).getResult().getTimestamp() : null;
                        DateTime validFrom = nextTs != null ? PeriodHelper.getPreviousPeriod(nextTs, inputPeriod, 1, false, timeZone) : null;
                        for (int i = j; i < j + nextAdd; i++) {
                            if (i < intervalsSize) {
                                JEVisSample sample1 = intervals.get(i).getResult();
                                if (validFrom != null && !sample1.getTimestamp().isAfter(validFrom)) continue;
                                sample1.setValue(value);
                                sample1.setNote(note);
                            }
                        }
                    }

                    if (nextAdd > 1) {
                        j += nextAdd - 1;
                    }
                }
            } else if (!currentInterval.isDifferential()) {
                /* quantity, raw period longer than the clean period (e.g. month values -> 15-minute values): every
                   interval gets its share of the raw value whose period contains it. The raw value may lie before
                   this batch (the processor works in batches, a batch can start in the middle of a month), so it
                   is looked up in the raw samples instead of in the intervals; intervals without a raw value (before
                   the first one) stay empty instead of 0 */
                DateTime timestamp = currentInterval.getResult().getTimestamp();
                while (rawIndex + 1 < rawSamples.size() && !rawSamples.get(rawIndex + 1).getTimestamp().isAfter(timestamp)) {
                    rawIndex++;
                }
                Double value = null;
                String note = currentInterval.getResult().getNote();
                if (rawIndex >= 0) {
                    try {
                        JEVisSample raw = rawSamples.get(rawIndex);
                        DateTime rawStart = raw.getTimestamp();
                        JEVisSample userValue = userDataMap.get(rawStart);
                        Double rawValue = userValue != null ? userValue.getValueAsDouble() : raw.getValueAsDouble();
                        if (countedIndex != rawIndex) {
                            /* the number of clean periods in the raw period, counted in local time: a month with the
                               change to summer time has 31 days but 30.96 days of 24 hours (2026-11-27) */
                            countedIndex = rawIndex;
                            countedEnd = PeriodHelper.getNextPeriod(rawStart, inputPeriod, 1, true, timeZone);
                            countedPeriods = 0;
                            DateTime step = rawStart.withZone(timeZone);
                            while (step.isBefore(countedEnd) && countedPeriods < 10_000_000L) {
                                DateTime next = PeriodHelper.addPeriodToDate(step, outputPeriod);
                                if (!next.isAfter(step)) break;
                                step = next;
                                countedPeriods++;
                            }
                        }
                        DateTime maxDate = countedEnd;
                        long periodCount = countedPeriods;
                        if (rawValue != null && periodCount > 0 && timestamp.isBefore(maxDate)) {
                            value = getScaledValue(listMultipliers, rawStart, rawValue, offset).doubleValue() / periodCount;
                            String agg = "agg(yes,up," + periodCount + ",sum)";
                            note = note == null || note.isEmpty() ? agg : note + "," + agg;
                        }
                    } catch (Exception e) {
                        logger.error("Could not split the raw value for {}, object {}:{}", timestamp, cleanDataObject.getCleanObject().getName(), cleanDataObject.getCleanObject().getID(), e);
                    }
                }
                currentInterval.getResult().setValue(value);
                currentInterval.getResult().setNote(note);
            }
            /* counter readings split into shorter periods: see splitCounterReadings after the loop */
        }

        splitCounterReadings(intervals, rawSamples, timeZone);

        intervals.removeAll(needToBeRemoved);

        /* intervals that end before the first raw value of the row have no data: no value instead of 0 (2026-11-27) */
        DateTime firstRaw = null;
        try {
            firstRaw = cleanDataObject.getRawAttribute().getTimestampOfFirstSample();
        } catch (Exception e) {
            logger.warn("Could not read the first raw time stamp of {}", cleanDataObject.getCleanObject().getID(), e);
        }
        if (firstRaw != null) {
            for (CleanInterval interval : intervals) {
                if (interval.getRawSamples().isEmpty() && !interval.getInterval().getEnd().isAfter(firstRaw)
                        && !interval.getResult().getTimestamp().isAfter(firstRaw)) {
                    interval.getResult().setValue(null);
                }
            }
        }
        /* intervals of the same or a longer period that start after the last raw value have no data yet */
        if (!rawSamples.isEmpty()) {
            try {
                DateTime lastRaw = rawSamples.get(rawSamples.size() - 1).getTimestamp();
                for (CleanInterval interval : intervals) {
                    if (interval.getCompare() >= 0 && interval.getRawSamples().isEmpty() && !interval.getInterval().getStart().isBefore(lastRaw)) {
                        interval.getResult().setValue(null);
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not read the last raw time stamp", e);
            }
        }

        logger.debug("{} finished in {}", this.getClass().getSimpleName(), new Period(benchStart, new DateTime()).toString(PeriodFormat.wordBased(I18n.getInstance().getLocale())));
    }

    /**
     * Counter readings split into shorter clean periods (e.g. 15-minute readings -> 5 minutes, day readings -> hours):
     * the consumption of a reading (the result of its interval) is spread evenly over the clean periods since the
     * reading before it, which is taken from the raw samples, so a batch starting inside a raw period or a gap in the
     * readings get the right share (before 2026-11-27 the share was the number of empty intervals found in the batch).
     * Clean periods shorter than a day are stamped at their end and get the readings in (previous, reading]; days,
     * weeks and months are stamped at their start and get [previous, reading).
     */
    private static void splitCounterReadings(List<CleanInterval> intervals, List<JEVisSample> rawSamples, DateTimeZone timeZone) {
        List<Integer> readingIndex = new ArrayList<>();
        List<Double> readingDiff = new ArrayList<>();
        List<DateTime> readingTs = new ArrayList<>();
        for (int k = 0; k < intervals.size(); k++) {
            CleanInterval ci = intervals.get(k);
            if (ci.getCompare() < 0 && ci.isDifferential() && !ci.getRawSamples().isEmpty()) {
                try {
                    readingIndex.add(k);
                    readingDiff.add(ci.getResult().getValueAsDouble());
                    readingTs.add(ci.getRawSamples().get(ci.getRawSamples().size() - 1).getTimestamp());
                } catch (Exception e) {
                    logger.warn("Could not read the counter reading of {}", ci.getDate(), e);
                }
            }
        }
        for (CleanInterval ci : intervals) {
            if (ci.getCompare() < 0 && ci.isDifferential()) ci.getResult().setValue(null);
        }
        for (int r = 0; r < readingIndex.size(); r++) {
            Double diff = readingDiff.get(r);
            DateTime reading = readingTs.get(r);
            if (diff == null || reading == null) continue;
            DateTime previous = null;
            for (JEVisSample sample : rawSamples) {
                try {
                    if (!sample.getTimestamp().isBefore(reading)) break;
                    previous = sample.getTimestamp();
                } catch (Exception e) {
                    break;
                }
            }
            /* the first reading has no consumption */
            if (previous == null) continue;
            CleanInterval readingInterval = intervals.get(readingIndex.get(r));
            Period output = readingInterval.getOutputPeriod();
            boolean startStamped = output.getDays() > 0 || output.getWeeks() > 0 || output.getMonths() > 0 || output.getYears() > 0;
            long count = 0;
            DateTime step = previous.withZone(timeZone);
            while (step.isBefore(reading) && count < 10_000_000L) {
                DateTime next = PeriodHelper.addPeriodToDate(step, output);
                if (!next.isAfter(step)) break;
                step = next;
                count++;
            }
            if (count <= 0) continue;
            double value = diff / count;
            String agg = "agg(yes,up," + count + ",sum)";
            for (int i = readingIndex.get(r); i >= 0; i--) {
                CleanInterval ci = intervals.get(i);
                DateTime ts = ci.getResult().getTimestamp();
                boolean inside = startStamped ? (!ts.isBefore(previous) && ts.isBefore(reading)) : (ts.isAfter(previous) && !ts.isAfter(reading));
                if (startStamped ? ts.isBefore(previous) : !ts.isAfter(previous)) break;
                if (!inside || ci.getCompare() >= 0 || !ci.isDifferential()) continue;
                ci.getResult().setValue(value);
                String note = ci.getResult().getNote();
                ci.getResult().setNote(note == null || note.isEmpty() ? agg : note + "," + agg);
            }
        }
    }

    /** the last raw sample at or before the start of the interval, null if there is none */
    private static JEVisSample readingBefore(List<JEVisSample> rawSamples, CleanInterval interval) {
        DateTime start = interval.getInterval().getStart();
        JEVisSample found = null;
        for (JEVisSample sample : rawSamples) {
            try {
                if (sample.getTimestamp().isAfter(start)) break;
                found = sample;
            } catch (Exception e) {
                return found;
            }
        }
        return found;
    }

    private BigDecimal getScaledValue(List<JEVisSample> listMultipliers, DateTime date, Double currentValue, BigDecimal offset) {
        BigDecimal productDec = new BigDecimal(0);

        try {
            BigDecimal rawValueDec = new BigDecimal(currentValue.toString());
            productDec = productDec.add(rawValueDec);
            productDec = productDec.multiply(ScalingStep.getCurrentMultiplier(listMultipliers, date));
            productDec = productDec.add(offset);
        } catch (Exception e) {
        }

        return productDec;
    }


    private Double calcAvgSample(List<JEVisSample> currentTmpSamples, Boolean differential, CleanInterval
            lastInterval) throws Exception {
        Double value = 0.0;
        if (!differential) {
            for (JEVisSample sample : currentTmpSamples) {
                Double valueAsDouble = sample.getValueAsDouble();
                value += valueAsDouble;
            }
        } else {
            Double lastValue = lastInterval.getRawSamples().get(lastInterval.getRawSamples().size() - 1).getValueAsDouble();
            List<Double> tmpValues = new ArrayList<>();

            for (JEVisSample sample : currentTmpSamples) {
                Double valueAsDouble = sample.getValueAsDouble() - lastValue;
                tmpValues.add(valueAsDouble);
                lastValue = sample.getValueAsDouble();
            }

            for (Double d : tmpValues) {
                value += d;
            }
        }
        return (value / currentTmpSamples.size());
    }

    private Double calcSumSampleDownscale(List<JEVisSample> currentTmpSamples, Boolean
            differential, CleanInterval lastInterval) throws Exception {
        double value = 0.0;
        if (!currentTmpSamples.isEmpty()) {
            if (!differential) {
                for (JEVisSample sample : currentTmpSamples) {
                    value += sample.getValueAsDouble();
                }
            } else {
                Double lastValue = currentTmpSamples.get(currentTmpSamples.size() - 1).getValueAsDouble();
                Double firstValue = lastInterval.getRawSamples().get(lastInterval.getRawSamples().size() - 1).getValueAsDouble();
                value = lastValue - firstValue;
            }
        }
        return value;
    }
}
