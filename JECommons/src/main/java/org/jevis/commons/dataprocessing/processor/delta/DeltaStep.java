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
package org.jevis.commons.dataprocessing.processor.delta;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisSample;
import org.jevis.commons.constants.NoteConstants;
import org.jevis.commons.dataprocessing.CleanDataObject;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.dataprocessing.processor.GapsAndLimits;
import org.jevis.commons.dataprocessing.processor.limits.LimitBreak;
import org.jevis.commons.dataprocessing.processor.limits.MinOrMax;
import org.jevis.commons.dataprocessing.processor.workflow.CleanInterval;
import org.jevis.commons.dataprocessing.processor.workflow.ProcessStep;
import org.jevis.commons.dataprocessing.processor.workflow.ResourceManager;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.json.JsonDeltaConfig;
import org.jevis.commons.json.JsonGapFillingConfig;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jevis.commons.constants.GapFillingType.parse;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
public class DeltaStep implements ProcessStep {

    private static final Logger logger = LogManager.getLogger(DeltaStep.class);

    @Override
    public void run(ResourceManager resourceManager) throws Exception {
        DateTime benchStart = new DateTime();
        CleanDataObject cleanDataObject = resourceManager.getCleanDataObject();

        if (!cleanDataObject.getDeltaEnabled() || cleanDataObject.getDeltaConfig() == null) {
            //no delta check when disabled or no config
            return;
        }
        List<CleanInterval> intervals = resourceManager.getIntervals();
        JEVisAttribute cleanAttribute = cleanDataObject.getValueAttribute();
        JsonDeltaConfig deltaConfig = cleanDataObject.getDeltaConfig();
        JsonGapFillingConfig maxConfig = deltaConfig.getMaxConfig();
        long maxSpikeMillis = maxConfig != null ? defaultValue(maxConfig.getBoundary()) : 0L;

        /* where the previous run stopped: the last clean value that is no delta break is the reference level; clean
           values with a delta note after it are a break that is still going on */
        History history = history(cleanAttribute);

        Detection step1 = detect(intervals, Double.parseDouble(deltaConfig.getMin()), history, maxSpikeMillis);
        Detection step2 = detect(intervals, Double.parseDouble(deltaConfig.getMax()), history, maxSpikeMillis);
        List<LimitBreak> deltaBreaksStep1 = new ArrayList<>(step1.spikes);
        deltaBreaksStep1.addAll(step1.levelChanges);
        /* only spikes are substituted: a new level that lasts longer than the boundary is real */
        List<LimitBreak> deltaBreaksStep2 = step2.spikes;

        if (deltaBreaksStep1.isEmpty() && deltaBreaksStep2.isEmpty()) { //no limit checks when there is no alignment
            logger.info("No delta breaks identified.");
            return;
        }
        logger.info("{} delta breaks for step 1 identified ({} level changes)", deltaBreaksStep1.size(), step1.levelChanges.size());
        logger.info("{} delta breaks for step 2 identified ({} level changes not substituted)", deltaBreaksStep2.size(), step2.levelChanges.size());

        NumberFormat nf = resourceManager.getNumberFormat();

        for (LimitBreak limitBreak : deltaBreaksStep1) {
            for (CleanInterval currentInterval : limitBreak.getIntervals()) {
                VirtualSample smp = currentInterval.getResult();
                String note = "";
                note += smp.getNote();
                note += "," + NoteConstants.Deltas.DELTA_STEP1;
                if (limitBreak instanceof DeltaBreak) {
                    DeltaBreak deltaBreak = (DeltaBreak) limitBreak;
                    note += ",(" + nf.format(deltaBreak.getChange()) + "," + nf.format(deltaBreak.getThreshold()) + ")";
                }
                smp.setNote(note);
            }
        }

        if (!deltaBreaksStep2.isEmpty()) {
            List<JEVisSample> sampleCache = resourceManager.getSampleCache();

            if (sampleCache == null) {
                sampleCache = intervals.stream().map(CleanInterval::getResult).collect(Collectors.toList());
                sampleCache.sort(Comparator.comparing(jeVisSample -> {
                    try {
                        return jeVisSample.getTimestamp();
                    } catch (JEVisException e) {
                        e.printStackTrace();
                    }
                    return null;
                }));
            }


            List<LimitBreak> filledLimitBreaks = new ArrayList<>();
            List<LimitBreak> newLimitBreaks = new ArrayList<>();
            JsonGapFillingConfig c = maxConfig;

            for (LimitBreak lb : deltaBreaksStep2) {
                if (!filledLimitBreaks.contains(lb)) {
                    logger.debug("[{}] start filling with Mode for {}", cleanDataObject.getCleanObject().getID(), c.getType());
                    DateTime firstDate = lb.getIntervals().get(0).getDate();
                    DateTime lastDate = lb.getIntervals().get(lb.getIntervals().size() - 1).getDate();
                    if ((lastDate.getMillis() - firstDate.getMillis()) <= defaultValue(c.getBoundary())) {
                        newLimitBreaks.add(lb);
                        filledLimitBreaks.add(lb);
                    }
                }
            }

            GapsAndLimits gal = new GapsAndLimits(intervals, null, GapsAndLimits.GapsAndLimitsType.DELTA_TYPE,
                    c, new ArrayList<>(), newLimitBreaks, sampleCache, cleanDataObject);

            switch (parse(c.getType())) {
                case STATIC:
                    gal.fillStatic();
                    break;
                case INTERPOLATION:
                    gal.fillInterpolation();
                    break;
                case DEFAULT_VALUE:
                    gal.fillDefault();
                    break;
                case MINIMUM:
                    gal.fillMinimum();
                    break;
                case MAXIMUM:
                    gal.fillMaximum();
                    break;
                case MEDIAN:
                    gal.fillMedian();
                    break;
                case AVERAGE:
                    gal.fillAverage();
                    break;
                case DELETE:
                    gal.fillDelete();
                    break;
                case NONE:
                default:
                    break;
            }

            logger.debug("[{}] Done", resourceManager.getID());

            if (deltaBreaksStep2.size() != filledLimitBreaks.size())
                logger.error("Could not complete all delta breaks. Delta break may have been too long for reasonable gap filling.");
        }

        logger.debug("[{}] finished substituting values", cleanDataObject.getCleanObject().getID());
        logger.debug("{} finished in {}", this.getClass().getSimpleName(), new Period(benchStart, new DateTime()).toString(PeriodFormat.wordBased(I18n.getInstance().getLocale())));
    }

    private Long defaultValue(String s) {
        long l = 0L;
        if (Objects.nonNull(s)) {
            l = Long.parseLong(s);
        }
        return l;
    }

    /**
     * Reference level and an ongoing break at the end of the previous run, from the stored clean values.
     */
    static final class History {
        /** last stored clean value without a delta note, null if none */
        final Double reference;
        /** time of the first stored clean value of a break that was still going on at the end, null if none */
        final DateTime openBreakStart;

        History(Double reference, DateTime openBreakStart) {
            this.reference = reference;
            this.openBreakStart = openBreakStart;
        }
    }

    /** how far back the stored clean values are read for the history */
    static final int HISTORY_DAYS = 31;

    private static History history(JEVisAttribute cleanAttribute) {
        try {
            if (cleanAttribute == null || !cleanAttribute.hasSample()) return new History(null, null);
            JEVisSample latest = cleanAttribute.getLatestSample();
            if (latest == null) return new History(null, null);
            List<JEVisSample> samples = cleanAttribute.getSamples(latest.getTimestamp().minusDays(HISTORY_DAYS), latest.getTimestamp());
            return history(samples);
        } catch (Exception e) {
            logger.warn("Could not read the stored clean values for the delta check", e);
            return new History(null, null);
        }
    }

    /**
     * @param samples stored clean values, oldest first
     */
    static History history(List<JEVisSample> samples) throws JEVisException {
        DateTime openBreakStart = null;
        for (int i = samples.size() - 1; i >= 0; i--) {
            JEVisSample sample = samples.get(i);
            String note = sample.getNote();
            if (note != null && note.contains("delta(")) {
                openBreakStart = sample.getTimestamp();
                continue;
            }
            Double value = sample.getValueAsDouble();
            if (value != null) return new History(value, openBreakStart);
        }
        return new History(null, openBreakStart);
    }

    /** a break of the delta check with its relative change and threshold (for the note) */
    static final class DeltaBreak extends LimitBreak {
        private final double change;
        private final double threshold;

        DeltaBreak(double change, double threshold) {
            /* no min/max: the substituted values must not be clamped to the relative change (they were before) */
            super(null, null);
            this.change = change;
            this.threshold = threshold;
        }

        double getChange() {
            return change;
        }

        double getThreshold() {
            return threshold;
        }
    }

    /** spikes (to be substituted) and level changes (only marked) */
    static final class Detection {
        final List<LimitBreak> spikes = new ArrayList<>();
        final List<LimitBreak> levelChanges = new ArrayList<>();
    }

    /**
     * Relative change of a value against the reference level, as before: the difference divided by the new value
     * (or by the reference when the new value is 0).
     */
    static double change(double value, double reference) {
        double diff = Math.abs(value - reference);
        if (diff > 0d && value != 0d) return diff / Math.abs(value);
        if (diff > 0d && reference != 0d) return diff / Math.abs(reference);
        return 0d;
    }

    /**
     * Finds sudden jumps. Each value is compared with the last value taken as normal (the reference level), not with
     * the value before it: a break starts when a value differs from the reference by at least the threshold and ends
     * when a value is back near the reference, so the values in between are the spike and the first normal value after
     * it is not part of it. A deviation that lasts longer than {@code maxSpikeMillis} is a new level: it is only
     * marked (level change), and the reference follows it.
     * <p>
     * Threshold 0 keeps the old meaning: values that do not change at all (frozen values).
     *
     * @param deltaPercent   threshold in percent
     * @param history        reference level and an ongoing break from the previous run
     * @param maxSpikeMillis longest spike (the boundary of the substitution); longer deviations are level changes
     */
    static Detection detect(List<CleanInterval> intervals, double deltaPercent, History history, long maxSpikeMillis) {
        Detection detection = new Detection();
        double delta = deltaPercent / 100;
        if (delta == 0d) {
            detection.spikes.addAll(frozenValues(intervals, history.reference != null ? history.reference : 0d));
            return detection;
        }
        Double reference = history.reference;
        DeltaBreak current = null;
        DateTime breakStart = null;
        CleanInterval lastNormal = null;
        for (CleanInterval interval : intervals) {
            VirtualSample sample = interval.getResult();
            Double value = sample != null ? sample.getValueAsDouble() : null;
            if (value == null) {
                logger.warn("Delta Error. No value for Delta Sample {}", sample);
                continue;
            }
            if (reference == null) {
                reference = value;
                lastNormal = interval;
                continue;
            }
            double d = change(value, reference);
            if (current == null) {
                if (d >= delta) {
                    current = new DeltaBreak(d, delta);
                    current.setFirstValue(lastNormal != null ? lastNormal.getResult().getValueAsDouble() : reference);
                    current.setMinOrMax(MinOrMax.MAX);
                    current.addInterval(interval);
                    /* a break that was still going on at the end of the previous run started then */
                    breakStart = lastNormal == null && history.openBreakStart != null ? history.openBreakStart : interval.getDate();
                } else {
                    reference = value;
                    lastNormal = interval;
                }
                continue;
            }
            if (d < delta) {
                /* back to the reference level: the break was a spike */
                current.setLastValue(value);
                detection.spikes.add(current);
                current = null;
                reference = value;
                lastNormal = interval;
            } else if (interval.getDate().getMillis() - breakStart.getMillis() > maxSpikeMillis) {
                /* the deviation lasts: a new level, only marked; the reference follows it */
                current.addInterval(interval);
                current.setLastValue(value);
                detection.levelChanges.add(current);
                current = null;
                reference = value;
                lastNormal = interval;
            } else {
                current.addInterval(interval);
            }
        }
        if (current != null) {
            /* still going on at the end: substituted if short enough (as before); the next run continues it */
            List<CleanInterval> list = current.getIntervals();
            current.setLastValue(list.get(list.size() - 1).getResult().getValueAsDouble());
            detection.spikes.add(current);
        }
        return detection;
    }

    /**
     * Threshold 0: runs of values that do not change (unchanged behaviour).
     */
    private static List<LimitBreak> frozenValues(List<CleanInterval> intervals, double firstValue) {
        List<LimitBreak> limitBreaks = new ArrayList<>();
        LimitBreak currentLimitBreak = null;
        CleanInterval lastInterval = null;
        double lastValue = Math.abs(firstValue);
        for (CleanInterval currentInterval : intervals) {
            VirtualSample sample = currentInterval.getResult();
            if (sample != null && sample.getValueAsDouble() != null) {
                double sampleValue = Math.abs(sample.getValueAsDouble());
                double diff = Math.abs(sampleValue - lastValue);
                lastValue = sampleValue;
                if (diff == 0d) {
                    if (currentLimitBreak == null) {
                        currentLimitBreak = new DeltaBreak(0d, 0d);
                        currentLimitBreak.setFirstValue(lastInterval != null ? lastInterval.getResult().getValueAsDouble() : firstValue);
                        currentLimitBreak.addInterval(currentInterval);
                        currentLimitBreak.setMinOrMax(MinOrMax.MAX);
                    } else {
                        currentLimitBreak.addInterval(currentInterval);
                    }
                } else if (currentLimitBreak != null) {
                    currentLimitBreak.setLastValue(sampleValue);
                    limitBreaks.add(currentLimitBreak);
                    currentLimitBreak = null;
                }
            }
            lastInterval = currentInterval;
        }
        if (currentLimitBreak != null && !currentLimitBreak.getIntervals().isEmpty()) {
            List<CleanInterval> list = currentLimitBreak.getIntervals();
            currentLimitBreak.setLastValue(list.get(list.size() - 1).getResult().getValueAsDouble());
            limitBreaks.add(currentLimitBreak);
        }
        return limitBreaks;
    }
}
