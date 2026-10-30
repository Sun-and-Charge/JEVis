/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.commons.calculation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisSample;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.datetime.PeriodHelper;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;

import java.util.*;

/**
 * Merges sample lists from multiple {@link CalcInputObject} inputs into a single
 * {@code TreeMap<DateTime, List<Sample>>} keyed by timestamp.
 * <p>
 * Supported {@link CalcInputType} modes:
 * <ul>
 *   <li><b>PERIODIC</b> — regular-interval time-series; one sample per timestamp.</li>
 *   <li><b>ASYNC</b>    — event-driven. If only async inputs exist, results are calculated at
 *       every timestamp of any input and missing values are zero-filled. Together with periodic
 *       inputs the async values are assigned to the period of the periodic timestamps
 *       {@code [t, t + period)} (upstream issue #2310): quantities (kWh, m³, …) are summed per
 *       period (0 if there is none), other values (power, temperature, price, state) use the last
 *       value at or before the start of the period; periods before the first value are skipped.</li>
 *   <li><b>STATIC</b>   — single constant applied to every timestamp in the merged map.</li>
 *   <li><b>NON_PERIODIC</b> — step-function: the most-recently-seen value is used at each
 *       timestamp until the next change.</li>
 * </ul>
 * Incomplete rows (missing non-async inputs) are removed before the map is returned.
 */
public class SampleMerger {

    private static final Logger logger = LogManager.getLogger(SampleMerger.class);
    private final List<List<Sample>> allSamples = new ArrayList<>();
    private final List<Sample> constants = new ArrayList<>();
    private final List<List<Sample>> periodConstants = new ArrayList<>();
    private final List<String> asyncVariables = new ArrayList<>();
    private int noOfAsyncVariables = 0;
    private int noOfAsyncAndPeriodicVariables = 0;
    private final Map<String, List<Sample>> asyncSamples = new HashMap<>();
    private final Set<String> asyncQuantities = new HashSet<>();
    private Period period;

    /**
     * Sets the period of the periodic inputs. It defines the end of the period of each periodic timestamp
     * to which async values are assigned. Without a period the next periodic timestamp is used.
     */
    public void setPeriod(Period period) {
        this.period = period;
    }

    private void addPeriodic(List<JEVisSample> jevisSamples, String variable, CalcInputType calcInputType) {
        List<Sample> samples = new ArrayList<>();
        for (JEVisSample currentSample : jevisSamples) {
            Sample sample = new Sample(currentSample, variable, calcInputType);
            samples.add(sample);
        }
        allSamples.add(samples);
    }

    private void addConstant(JEVisSample constant, String variable, CalcInputType calcInputType) {
        Sample sample = new Sample(constant, variable, calcInputType);
        constants.add(sample);
    }

    private void addPeriodConstant(List<JEVisSample> jevisSamples, String variable, CalcInputType calcInputType) {
        List<Sample> samples = new ArrayList<>();
        for (JEVisSample currentSample : jevisSamples) {
            Sample sample = new Sample(currentSample, variable, calcInputType);
            samples.add(sample);
        }
        periodConstants.add(samples);
    }

    public void addSamples(List<JEVisSample> jevisSamples, String variable, CalcInputType inputType) {
        addSamples(jevisSamples, variable, inputType, true);
    }

    /**
     * @param quantity only used for async inputs combined with periodic ones: true sums the async values per period,
     *                 false uses the last value at or before the start of the period
     */
    public void addSamples(List<JEVisSample> jevisSamples, String variable, CalcInputType inputType, boolean quantity) {
        if (inputType == CalcInputType.ASYNC) {
            List<Sample> samples = new ArrayList<>();
            for (JEVisSample jevisSample : jevisSamples) {
                samples.add(new Sample(jevisSample, variable, inputType));
            }
            samples.sort(Comparator.comparing(Sample::getDate));
            asyncSamples.put(variable, samples);
            if (quantity) {
                asyncQuantities.add(variable);
            }
        }
        switch (inputType) {
            case PERIODIC:
                noOfAsyncAndPeriodicVariables++;
                addPeriodic(jevisSamples, variable, inputType);
                break;
            case ASYNC:
                noOfAsyncVariables++;
                noOfAsyncAndPeriodicVariables++;
                asyncVariables.add(variable);
                addPeriodic(jevisSamples, variable, inputType);
                break;
            case STATIC:
                addConstant(jevisSamples.get(0), variable, inputType);
                break;
            case NON_PERIODIC:
                addPeriodConstant(jevisSamples, variable, inputType);
                break;
        }
    }

    public Map<DateTime, List<Sample>> merge() {
        if (noOfAsyncVariables > 0 && noOfAsyncAndPeriodicVariables > noOfAsyncVariables) {
            return mergeAsyncIntoPeriods();
        }

        Map<DateTime, List<Sample>> sampleMap = new TreeMap<>();

        insertAllSamples(sampleMap); //value changed for every sample

        insertConstants(sampleMap); //value is always the same

        insertPeriodicConstants(sampleMap); //value changed for specific periods

        int variableSize = allSamples.size() + constants.size() + periodConstants.size();

        if (noOfAsyncVariables > 0) {
            for (Map.Entry<DateTime, List<Sample>> entry : sampleMap.entrySet()) {
                DateTime sampleTime = entry.getKey();
                List<Sample> sampleList = entry.getValue();

                if (sampleList.size() < variableSize) {
                    List<String> usedAsyncVariables = new ArrayList<>();
                    sampleList.forEach(sample -> {
                        if (sample.getCalcInputType() == CalcInputType.ASYNC) {
                            usedAsyncVariables.add(sample.getVariable());
                        }
                    });

                    for (String variable : asyncVariables) {
                        if (!usedAsyncVariables.contains(variable)) {
                            VirtualSample virtualSample = new VirtualSample(sampleTime, 0.0);
                            Sample smp = new Sample(virtualSample, variable, CalcInputType.ASYNC);
                            sampleList.add(smp);
                        }
                    }
                }
            }
        }

        Set<DateTime> removableKeys = new HashSet<>();
        for (Map.Entry<DateTime, List<Sample>> sampleEntry : sampleMap.entrySet()) {
            if (sampleEntry.getValue().size() + noOfAsyncVariables < variableSize) {
                DateTime dateTimeListEntryKey = sampleEntry.getKey();
                removableKeys.add(dateTimeListEntryKey);
            }
        }

        removableKeys.forEach(key -> {
            logger.debug("not every input data with datetime {}, will delete this datetime from calculation", key.toString(DateTimeFormat.fullDateTime()));
            sampleMap.remove(key);
        });

        boolean allAsync = noOfAsyncAndPeriodicVariables == noOfAsyncVariables;
        if (allAsync) {
            logger.debug("Only Async Variables found. Removing all values after last of variable with the least values");
            DateTime dateTime = null;
            DateTime firstDateTime = null;
            for (List<Sample> samples : allSamples) {
                DateTime sampleDateTime = samples.get(samples.size() - 1).getDate();
                if (dateTime == null || dateTime.isAfter(sampleDateTime)) {
                    dateTime = sampleDateTime;
                }
                if (firstDateTime == null) {
                    firstDateTime = sampleDateTime;
                }
            }

            if (dateTime != null && !dateTime.equals(firstDateTime)) {
                List<DateTime> toRemoveList = new ArrayList<>();
                for (Map.Entry<DateTime, List<Sample>> entry : sampleMap.entrySet()) {
                    DateTime dateTimeKey = entry.getKey();
                    if (dateTimeKey.equals(dateTime) || dateTimeKey.isAfter(dateTime)) {
                        toRemoveList.add(dateTimeKey);
                    }
                }

                logger.debug("Least date found is {}. Removing {} samples after", dateTime, toRemoveList.size());
                toRemoveList.forEach(sampleMap::remove);
            }
        }

        return sampleMap;
    }

    /**
     * Periodic and async inputs: the timestamps of the periodic inputs define the result timestamps, the async
     * values are assigned to the period they fall into.
     */
    private Map<DateTime, List<Sample>> mergeAsyncIntoPeriods() {
        TreeMap<DateTime, List<Sample>> sampleMap = new TreeMap<>();
        for (List<Sample> samples : allSamples) {
            for (Sample sample : samples) {
                if (sample.getCalcInputType() != CalcInputType.ASYNC) {
                    sampleMap.computeIfAbsent(sample.getDate(), k -> new ArrayList<>()).add(sample);
                }
            }
        }

        insertConstants(sampleMap);
        insertPeriodicConstants(sampleMap);

        int variableSize = allSamples.size() + constants.size() + periodConstants.size();
        Set<DateTime> removableKeys = new HashSet<>();
        DateTime previous = null;
        for (Map.Entry<DateTime, List<Sample>> entry : sampleMap.entrySet()) {
            DateTime start = entry.getKey();
            DateTime end = getPeriodEnd(sampleMap, start, previous);
            previous = start;

            for (String variable : asyncVariables) {
                List<Sample> samples = asyncSamples.getOrDefault(variable, Collections.emptyList());
                Double value;
                if (asyncQuantities.contains(variable)) {
                    double sum = 0d;
                    for (Sample sample : samples) {
                        if (!sample.getDate().isBefore(end)) {
                            break;
                        }
                        if (!sample.getDate().isBefore(start)) {
                            sum += sample.getValue().doubleValue();
                        }
                    }
                    value = sum;
                } else {
                    value = null;
                    for (Sample sample : samples) {
                        if (sample.getDate().isAfter(start)) {
                            break;
                        }
                        value = sample.getValue().doubleValue();
                    }
                }

                if (value != null) {
                    entry.getValue().add(new Sample(new VirtualSample(start, value), variable, CalcInputType.ASYNC));
                }
            }

            if (entry.getValue().size() < variableSize) {
                removableKeys.add(start);
            }
        }

        removableKeys.forEach(key -> {
            logger.debug("not every input data with datetime {}, will delete this datetime from calculation", key);
            sampleMap.remove(key);
        });

        return sampleMap;
    }

    private DateTime getPeriodEnd(TreeMap<DateTime, List<Sample>> sampleMap, DateTime start, DateTime previous) {
        if (period != null && !period.equals(Period.ZERO)) {
            return PeriodHelper.addPeriodToDate(start, period);
        }
        DateTime next = sampleMap.higherKey(start);
        if (next != null) {
            return next;
        }
        if (previous != null) {
            return start.plus(start.getMillis() - previous.getMillis());
        }
        return start.plusMillis(1);
    }

    private void insertAllSamples(Map<DateTime, List<Sample>> sampleMap) {
        allSamples.forEach(currentSamples -> currentSamples.forEach(sample -> {
            List<Sample> samples = sampleMap.getOrDefault(sample.getDate(), new ArrayList<>());
            samples.add(sample);
            sampleMap.put(sample.getDate(), samples);
        }));
    }

    private void insertConstants(Map<DateTime, List<Sample>> sampleMap) {
        sampleMap.forEach((key, value) -> value.addAll(constants));
    }

    private void insertPeriodicConstants(Map<DateTime, List<Sample>> sampleMap) {

//        periodConstants.forEach(currentSamples -> currentSamples.forEach(sample -> {
//            if (sample.getCalcInputType().equals(CalcInputType.ASYNC)) {
//                sampleMap.computeIfAbsent(sample.getDate(), k -> new ArrayList<>());
//            }
//        }));

        for (Map.Entry<DateTime, List<Sample>> entry : sampleMap.entrySet()) {
            DateTime currentSampleTime = entry.getKey();
            List<Sample> value = entry.getValue();
            for (List<Sample> periodicConstants : periodConstants) {
                Sample validConstant = null;
                for (Sample periodicConstant : periodicConstants) {
                    DateTime startDate = periodicConstant.getDate();
                    if (periodicConstant.getCalcInputType() != CalcInputType.ASYNC && (startDate.isBefore(currentSampleTime) || startDate.isEqual(currentSampleTime))) {
                        validConstant = periodicConstant;
                    } else if (startDate.isAfter(currentSampleTime)) {
                        break;
                    }
                }
                if (validConstant != null) {
                    value.add(validConstant);
                }
            }
        }
    }


}
