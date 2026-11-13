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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.calculation.CalcJob;
import org.jevis.commons.calculation.CalcJobFactory;
import org.jevis.commons.constants.GapFillingType;
import org.jevis.commons.constants.NoteConstants;
import org.jevis.commons.database.SampleHandler;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.CleanDataObject;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.commons.json.JsonGapFillingConfig;
import org.jevis.commons.json.JsonLimitsConfig;
import org.jevis.commons.unit.ChartUnits.ChartUnits;
import org.jevis.commons.unit.ChartUnits.QuantityUnits;
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.unit.UnitManager;
import org.jevis.commons.utils.CommonMethods;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;

/**
 * Loads the data of one row of an analysis, port of the Control Center's {@code ChartDataRow.getSamples()} and what it
 * calls (object/attribute resolution, unit factorization, user data, day filter, absolute mode, calculations), plus
 * the per chart type handling of {@code XYChartSerie} (sum unit of aggregated rows), {@code PieChart} (no aggregation,
 * pie value) and {@code TableChartH} (absolute mode), and the statistics of {@code XYChartSerie.updateTableEntry}.
 * <p>
 * The source samples are never modified (the Control Center changes them in place); values are copied into
 * {@link ChartSample}s with timestamps in the time zone of the user.
 * <p>
 * Instances keep a {@link CalculationLookup} and may be reused for all rows of one request (one user).
 */
public class RowDataLoader {
    public static final String CLEAN_DATA_CLASS = "Clean Data";
    public static final List<String> DATA_CLASSES = Collections.unmodifiableList(Arrays.asList("Data", "Clean Data", "String Data",
            "Base Data", "Math Data", "Data Notes", "Forecast Data"));
    private static final Logger logger = LogManager.getLogger(RowDataLoader.class);
    private final JEVisDataSource ds;
    private final CalculationLookup calculationLookup;

    public RowDataLoader(JEVisDataSource ds) {
        this(ds, new CalculationLookup(ds));
    }

    public RowDataLoader(JEVisDataSource ds, CalculationLookup calculationLookup) {
        this.ds = ds;
        this.calculationLookup = calculationLookup;
    }

    /**
     * Loads the data of a row like the Control Center shows it in a chart of the given type.
     *
     * @param definition the row
     * @param chartType  the type of the chart containing the row (not the row's own type)
     * @param range      the resolved time frame (rows with an own interval use that instead)
     * @param dayFilter  the day filter of the analysis, may be {@code null}
     * @param settings   aggregation, manipulation, time zone, ...
     * @return the result; {@link RowStatus#NO_PERMISSION} if the data source returns no object for the row
     */
    public RowResult load(RowDefinition definition, AnalysisChartType chartType, TimeRange range, DayFilter dayFilter,
                          EvaluationSettings settings) {
        RowResult result = new RowResult();
        result.setKey(definition.getKey());

        JEVisObject selected;
        try {
            selected = ds.getObject(definition.getObjectId());
        } catch (Exception e) {
            logger.error("Could not get object {} of row {}", definition.getObjectId(), definition.getKey(), e);
            return new RowResult(definition.getKey(), RowStatus.ERROR, "Could not get object: " + e.getMessage());
        }
        if (selected == null) {
            return new RowResult(definition.getKey(), RowStatus.NO_PERMISSION, null);
        }

        try {
            Row row = new Row(definition, selected, settings, dayFilter);
            row.setRange(range);

            if (chartType == AnalysisChartType.PIE) {
                /* PieChart: clonedModel.setAggregationPeriod(AggregationPeriod.NONE) */
                row.aggregation = AggregationPeriod.NONE;
            } else if (chartType != null && chartType.isXYChart() && row.aggregation != AggregationPeriod.NONE) {
                /* XYChartSerie.generateSeriesFromSamples: aggregated rows are shown in the sum unit */
                JEVisUnit sumUnit = new QuantityUnits().getSumUnit(row.unit);
                if (sumUnit != null && !sumUnit.equals(row.unit)) {
                    row.unit = new JEVisUnitImp(sumUnit.getFormula(), sumUnit.getLabel(), String.valueOf(sumUnit.getPrefix()));
                }
            }
            if (chartType == AnalysisChartType.TABLE_H || settings.isAbsolute()) {
                /* TableChartH: xyChartSerie.getSingleRow().setAbsolute(true); dashboard widgets: setAutoAggregation(true) */
                row.absolute = true;
            }

            result.setUnit(UnitManager.getInstance().format(row.unit));
            result.setStringData(row.stringData);

            if (row.attribute == null) {
                result.setStatus(RowStatus.NO_DATA);
                result.setMessage("Object has no data attribute");
                result.setCalculation(row.calculation);
                return result;
            }

            List<ChartSample> samples = row.getSamples();
            if (settings.isLastSampleOnly() && samples.size() > 1) {
                /* DataModelDataHandler.update: a forced interval of zero length keeps the newest sample */
                samples = new ArrayList<>(samples.subList(samples.size() - 1, samples.size()));
            }
            result.setCount(row.count != null ? Integer.valueOf(row.count) : Integer.valueOf(samples.size()));
            result.setLastTimestamp(row.newest != null ? row.newest
                    : (samples.isEmpty() ? null : samples.get(samples.size() - 1).getTimestamp()));
            result.setCalculation(row.calculation);
            result.setSamples(samples);
            if (samples.isEmpty()) {
                result.setStatus(RowStatus.NO_DATA);
            } else {
                result.setStatus(RowStatus.OK);
                if (!row.stringData) {
                    result.setStats(row.statistics(samples));
                    if (chartType == AnalysisChartType.PIE) {
                        result.setPieValue(row.pieValue(samples));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Could not load data of row {}", definition.getKey(), e);
            result.setStatus(RowStatus.ERROR);
            result.setMessage(e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            result.setSamples(new ArrayList<>());
            result.setStats(null);
        }
        return result;
    }

    /**
     * Resolves the data attribute of a row like {@code ChartDataRow.getAttribute()}: "Value" of the clean data or
     * data object (for a clean data row the clean data's own attribute).
     *
     * @return the attribute or {@code null} if the object is not readable or no data object
     */
    public JEVisAttribute resolveAttribute(RowDefinition definition) {
        try {
            JEVisObject object = ds.getObject(definition.getObjectId());
            return object != null ? AnalysisReader.resolveValueAttribute(object) : null;
        } catch (Exception e) {
            logger.debug("Could not resolve attribute of row {}", definition.getKey(), e);
            return null;
        }
    }

    /**
     * Runtime state of one row, the fields of {@code ChartDataRow} that matter for the data.
     */
    private class Row {
        private final RowDefinition definition;
        private final DateTimeZone zone;
        private final boolean customWorkDay;
        private final DayFilter dayFilter;
        private final Locale holidayLocale;
        private final ManipulationMode manipulation;
        private final Double timeFactor = 1d;
        private JEVisObject object;
        private JEVisObject dataProcessor;
        private JEVisAttribute attribute;
        private boolean stringData = false;
        private JEVisUnit unit;
        private boolean calculation;
        private long calculationId;
        private JEVisObject calculationObject;
        private AggregationPeriod aggregation;
        private boolean absolute = false;
        private DateTime start;
        private DateTime end;
        private Period period;
        /* timestamp of the newest sample before the absolute mode reduces the samples */
        private DateTime newest;
        /* number of samples before the absolute mode reduces them */
        private Integer count;

        Row(RowDefinition definition, JEVisObject selected, EvaluationSettings settings, DayFilter dayFilter) throws JEVisException {
            this.definition = definition;
            this.zone = settings.getZone();
            this.customWorkDay = settings.isCustomWorkDay();
            this.dayFilter = dayFilter;
            this.holidayLocale = settings.getHolidayLocale();
            this.aggregation = settings.getAggregation();
            this.manipulation = settings.getManipulation();
            this.unit = definition.createUnit();

            /* ChartDataRow.getObjects(): a clean data row has the parent data object as object */
            if (CLEAN_DATA_CLASS.equals(selected.getJEVisClassName())) {
                dataProcessor = selected;
                object = CommonMethods.getFirstParentalDataObject(selected);
            } else {
                object = selected;
            }

            /* ChartDataRow.getAttribute(): always "Value" of the clean data or data object */
            String className = object.getJEVisClassName();
            if (DATA_CLASSES.contains(className)) {
                attribute = dataProcessor == null ? object.getAttribute(AnalysisReader.VALUE_ATTRIBUTE_NAME)
                        : dataProcessor.getAttribute(AnalysisReader.VALUE_ATTRIBUTE_NAME);
                if ("String Data".equals(className) || "Data Notes".equals(className)) {
                    stringData = true;
                }
            }

            /* ChartDataRow(ds, chartData): the calculation flag only stays set if a calculation targets the object */
            calculation = definition.isCalculation();
            long found = -1;
            if (calculation) {
                found = calculationLookup.isObjectCalculated(object);
                if (found == -1) calculation = false;
            }
            calculationId = definition.getCalculationId();
            if (calculation && calculationId == -1) {
                /* not in the Control Center (it keeps -1 and loads nothing), the legacy format stores no id */
                calculationId = found;
            }
        }

        void setRange(TimeRange range) {
            if (definition.isIntervalEnabled()) {
                DateTime todayStart = DateTime.now(zone).withTimeAtStartOfDay();
                start = parse(definition.getIntervalStart(), todayStart);
                end = parse(definition.getIntervalEnd(), DateTime.now(zone));
            } else {
                start = range.getStart().withZone(zone);
                end = range.getEnd().withZone(zone);
            }
        }

        private DateTime parse(String value, DateTime fallback) {
            if (value == null || value.isEmpty()) return fallback;
            try {
                return new DateTime(value, zone);
            } catch (Exception e) {
                logger.warn("Could not parse interval {} of row {}", value, definition.getKey());
                return fallback;
            }
        }

        /**
         * Port of {@code ChartDataRow.getSamples()}.
         */
        List<ChartSample> getSamples() throws Exception {
            List<ChartSample> samples = new ArrayList<>();
            if (start == null || end == null) return samples;
            if (start.isAfter(end)) {
                logger.error("No interval between timestamps for row {}. The end instant must be greater the start.", definition.getKey());
                return samples;
            }

            /* without aggregation the stored values of the calculation are used, also for the manipulation of value
               widgets (peak = maximum of the stored values) */
            if (!calculation || (aggregation == AggregationPeriod.NONE && (!absolute || perPeriodManipulation(manipulation)))) {
                List<JEVisSample> unmodifiedSamples = attribute.getSamples(start, end, customWorkDay, aggregation.toString(),
                        manipulation.toString(), zone.getID());
                if (!stringData) {
                    samples = factorizeSamples(copy(unmodifiedSamples, false));
                    applyUserData(samples);
                    samples = applyDayFilter(samples);

                    if (absolute && !samples.isEmpty()) {
                        newest = samples.get(samples.size() - 1).getTimestamp();
                        count = samples.size();
                        Double manipulatedData = getManipulatedData(samples);
                        ChartSample virtualSample = new ChartSample(samples.get(0).getTimestamp(), manipulatedData, null);
                        samples = new ArrayList<>();
                        samples.add(virtualSample);
                    }
                } else {
                    samples = applyDayFilter(copy(unmodifiedSamples, true));
                }
            } else {
                JEVisObject calculationObject = getCalculationObject();
                if (calculationObject == null) {
                    /* ChartDataRow: "This is not a calculation, getting normal samples" */
                    logger.warn("Calculation {} of row {} not available, getting normal samples", calculationId, definition.getKey());
                    calculation = false;
                    return getSamples();
                }

                CalcJobFactory calcJobCreator = new CalcJobFactory();
                CalcJob calcJob;
                if (absolute && aggregation != AggregationPeriod.NONE && perPeriodManipulation(manipulation)) {
                    /* value widgets: the absolute calculation is the total of the time frame and knows no
                       manipulation; for mean/min/max/median the calculation runs per aggregation period and the
                       manipulation is applied to these values, as for rows without calculation (demo 2026-11-13:
                       sum, daily mean and peak were the same value) */
                    calcJob = calcJobCreator.getCalcJobForTimeFrame(new SampleHandler(), ds, calculationObject, start, end, aggregation);
                    List<ChartSample> perPeriod = applyDayFilter(factorizeSamples(copy(calcJob.getResults(), false)));
                    samples = new ArrayList<>();
                    if (!perPeriod.isEmpty()) {
                        newest = perPeriod.get(perPeriod.size() - 1).getTimestamp();
                        count = perPeriod.size();
                        samples.add(new ChartSample(perPeriod.get(0).getTimestamp(), getManipulatedData(perPeriod), null));
                    }
                    return samples;
                }
                if (!absolute) {
                    calcJob = calcJobCreator.getCalcJobForTimeFrame(new SampleHandler(), ds, calculationObject, start, end, aggregation);
                } else {
                    calcJob = calcJobCreator.getCalcJobForTimeFrame(new SampleHandler(), ds, calculationObject, start, end, true);
                }

                samples = copy(calcJob.getResults(), false);
                if (!absolute) {
                    samples = applyDayFilter(samples);
                }

                if (!absolute && dataProcessor != null) {
                    applyLimitDefaultValues(samples);
                }
            }
            return samples;
        }

        private boolean perPeriodManipulation(ManipulationMode mode) {
            return mode == ManipulationMode.AVERAGE || mode == ManipulationMode.MIN || mode == ManipulationMode.MAX
                    || mode == ManipulationMode.MEDIAN;
        }

        private List<ChartSample> copy(List<JEVisSample> source, boolean asString) {
            List<ChartSample> result = new ArrayList<>();
            if (source == null) return result;
            for (JEVisSample sample : source) {
                try {
                    result.add(new ChartSample(sample.getTimestamp().withZone(zone), value(sample, asString), sample.getNote()));
                } catch (Exception e) {
                    logger.error("Could not read sample of row {}", definition.getKey(), e);
                }
            }
            return result;
        }

        private Object value(JEVisSample sample, boolean asString) throws JEVisException {
            if (asString) return sample.getValueAsString();
            try {
                Double value = sample.getValueAsDouble();
                if (value != null) return value;
            } catch (Exception ignored) {
            }
            return sample.getValueAsString();
        }

        /**
         * Port of {@code ChartDataRow.factorizeSamples}: converts to the unit of the row (e.g. Wh to kWh).
         */
        private List<ChartSample> factorizeSamples(List<ChartSample> inputList) {
            if (unit != null && !inputList.isEmpty()) {
                try {
                    String outputUnit = UnitManager.getInstance().format(unit).replace("·", "");
                    if (outputUnit.isEmpty()) outputUnit = unit.getLabel();

                    JEVisUnit displayUnit = attribute.getDisplayUnit();
                    String inputUnit = UnitManager.getInstance().format(displayUnit).replace("·", "");
                    if (inputUnit.isEmpty()) inputUnit = displayUnit.getLabel();

                    ChartUnits cu = new ChartUnits();
                    Period rawPeriod = CleanDataObject.getPeriodForDate(attribute.getObject(), start);
                    Period currentPeriod;
                    if (inputList.size() > 1) {
                        currentPeriod = new Period(inputList.get(0).getTimestamp(), inputList.get(1).getTimestamp());
                    } else currentPeriod = rawPeriod;

                    Double scaleFactor = cu.scaleValue(rawPeriod, inputUnit, currentPeriod, outputUnit);

                    for (ChartSample sample : inputList) {
                        try {
                            sample.setValue(sample.getDouble() * scaleFactor * timeFactor);
                        } catch (Exception e) {
                            logger.error("Error in sample {}:{} of row {}", sample.getTimestamp(), sample.getValue(), definition.getKey());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Could not factorize samples correctly for row {}", definition.getKey(), e);
                }
            }
            return inputList;
        }

        /**
         * Port of {@code ChartDataRow.getUserDataMap}/{@code applyUserData}: values of a "User Data" object next to the
         * data object replace the samples with the same timestamp (only without aggregation).
         */
        private void applyUserData(List<ChartSample> samples) {
            try {
                if (aggregation != AggregationPeriod.NONE) return;
                Map<Long, JEVisSample> userDataMap = getUserDataMap();
                if (userDataMap.isEmpty()) return;
                boolean replaced = false;
                for (ChartSample sample : samples) {
                    JEVisSample userDataSample = userDataMap.get(sample.getMillis());
                    if (userDataSample != null) {
                        sample.setValue(value(userDataSample, false));
                        replaced = true;
                    }
                }
                if (replaced) {
                    samples.sort(Comparator.comparing(ChartSample::getMillis));
                }
            } catch (Exception e) {
                logger.error("Could not apply user data correctly for row {}", definition.getKey(), e);
            }
        }

        private Map<Long, JEVisSample> getUserDataMap() throws JEVisException {
            Map<Long, JEVisSample> userDataMap = new TreeMap<>();
            JEVisClass userDataClass = ds.getJEVisClass("User Data");
            JEVisObject attributeObject = attribute.getObject();
            if (userDataClass == null || attributeObject == null) return userDataMap;
            List<JEVisObject> parents = attributeObject.getParents();
            if (parents == null || parents.isEmpty()) return userDataMap;
            for (JEVisObject obj : parents.get(0).getChildren(userDataClass, true)) {
                if (obj.getName().contains(attributeObject.getName())) {
                    JEVisAttribute userDataValueAttribute = obj.getAttribute(AnalysisReader.VALUE_ATTRIBUTE_NAME);
                    if (userDataValueAttribute != null && userDataValueAttribute.hasSample()) {
                        for (JEVisSample smp : userDataValueAttribute.getSamples(start, end)) {
                            userDataMap.put(smp.getTimestamp().getMillis(), smp);
                        }
                    }
                    break;
                }
            }
            return userDataMap;
        }

        /**
         * Port of {@code ChartDataRow.isDayFilterApplied}/{@code applyDayFilter}.
         */
        private List<ChartSample> applyDayFilter(List<ChartSample> samples) {
            if (samples == null || samples.isEmpty() || !isDayFilterApplied()) return samples;
            Predicate<LocalDate> holidays = null;
            if (dayFilter.isExcludeHolidays()) {
                JEVisObject site = null;
                try {
                    site = CommonMethods.getFirstParentalObjectOfClass(object, "Building");
                    if (site != null && !"Building".equals(site.getJEVisClassName())) site = null;
                } catch (Exception e) {
                    logger.debug("Could not get site object for day filter", e);
                }
                holidays = DayFilter.holidays(holidayLocale, site);
            }
            return dayFilter.filter(samples, zone, holidays);
        }

        private boolean isDayFilterApplied() {
            if (dayFilter == null || !dayFilter.isActive()) return false;
            Period rawPeriod = null;
            if (aggregation == AggregationPeriod.NONE) {
                rawPeriod = getPeriod();
            }
            return DayFilter.isApplicable(aggregation, rawPeriod);
        }

        /**
         * Port of {@code ChartDataRow.getPeriod()}: latest value of the attribute "Period".
         */
        private Period getPeriod() {
            if (period == null) {
                Period p = Period.ZERO;
                JEVisObject periodObject = dataProcessor != null ? dataProcessor : object;
                try {
                    JEVisAttribute periodAttribute = periodObject.getAttribute("Period");
                    if (periodAttribute != null) {
                        JEVisSample latestSample = periodAttribute.getLatestSample();
                        if (latestSample != null) {
                            p = new Period(latestSample.getValueAsString());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error while getting Period of row {}", definition.getKey(), e);
                }
                period = p;
            }
            return period;
        }

        /**
         * Port of {@code ChartDataRow.getManipulatedData} (absolute mode: one value for the whole time frame).
         * Two bugs of the Control Center are fixed: the maximum started at {@code Double.MIN_VALUE} (wrong for
         * negative values) and AVERAGE divided the mean of non-quantities a second time by the number of samples.
         */
        private Double getManipulatedData(List<ChartSample> samples) {
            Double value = 0d;
            if (samples.size() == 1) {
                try {
                    value = samples.get(0).getDouble();
                } catch (Exception e) {
                    logger.error("Could not get value for row {}", definition.getKey(), e);
                }
            } else if (samples.size() > 1) {
                try {
                    QuantityUnits qu = new QuantityUnits();
                    boolean isQuantity = qu.isQuantityUnit(unit);
                    isQuantity = qu.isQuantityIfCleanData(attribute, isQuantity);

                    double min = Double.MAX_VALUE;
                    double max = -Double.MAX_VALUE;
                    List<Double> listMedian = new ArrayList<>();
                    for (ChartSample sample : samples) {
                        double currentValue = sample.getDouble();
                        value += currentValue;
                        min = Math.min(min, currentValue);
                        max = Math.max(max, currentValue);
                        listMedian.add(currentValue);
                    }
                    if (!isQuantity) {
                        value = value / samples.size();
                    }

                    switch (manipulation) {
                        case AVERAGE:
                            if (isQuantity) value = value / (double) samples.size();
                            break;
                        case MIN:
                            value = min;
                            break;
                        case MAX:
                            value = max;
                            break;
                        case MEDIAN:
                            if (listMedian.size() > 1)
                                listMedian.sort(Comparator.naturalOrder());
                            value = listMedian.get((listMedian.size() - 1) / 2);
                            break;
                        default:
                            break;
                    }
                } catch (Exception ex) {
                    logger.error("Error in quantity check of row {}", definition.getKey(), ex);
                }
            }
            return value;
        }

        /**
         * The block of {@code ChartDataRow.getSamples()} for calculated clean data: values outside of the second limit
         * configuration are replaced by the default value of the second gap filling configuration.
         */
        private void applyLimitDefaultValues(List<ChartSample> samples) {
            CleanDataObject cleanDataObject = new CleanDataObject(dataProcessor);
            if (!Boolean.TRUE.equals(cleanDataObject.getLimitsEnabled())) return;
            List<JsonLimitsConfig> limitsConfig = cleanDataObject.getLimitsConfig();
            Double maxDouble = null;
            Double minDouble = null;
            for (JsonLimitsConfig jsonLimitsConfig : limitsConfig) {
                if (limitsConfig.indexOf(jsonLimitsConfig) == 1) {
                    try {
                        maxDouble = Double.parseDouble(jsonLimitsConfig.getMax());
                    } catch (Exception e) {
                        logger.error("Could not parse string {} to double", jsonLimitsConfig.getMax());
                    }
                    try {
                        minDouble = Double.parseDouble(jsonLimitsConfig.getMin());
                    } catch (Exception e) {
                        logger.error("Could not parse string {} to double", jsonLimitsConfig.getMin());
                    }
                }
            }
            if (maxDouble == null && minDouble == null) return;

            Double replacementValue = null;
            List<JsonGapFillingConfig> gapFillingConfig = cleanDataObject.getGapFillingConfig();
            for (JsonGapFillingConfig jsonGapFillingConfig : gapFillingConfig) {
                GapFillingType fillingType = GapFillingType.parse(jsonGapFillingConfig.getType());
                if (gapFillingConfig.indexOf(jsonGapFillingConfig) == 1 && fillingType == GapFillingType.DEFAULT_VALUE) {
                    try {
                        replacementValue = Double.parseDouble(jsonGapFillingConfig.getDefaultvalue());
                    } catch (Exception e) {
                        logger.error("Could not parse string {} to double", jsonGapFillingConfig.getDefaultvalue());
                    }
                }
            }
            if (replacementValue == null) return;

            for (ChartSample sample : samples) {
                try {
                    double value = sample.getDouble();
                    if ((maxDouble != null && value > maxDouble) || (minDouble != null && value < minDouble)) {
                        sample.setValue(replacementValue);
                        String note = "";
                        note += sample.getNote();
                        note += "," + NoteConstants.Limits.LIMIT_DEFAULT;
                        sample.setNote(note);
                    }
                } catch (Exception e) {
                    logger.error("Could not get replacement value for ts {} of row {}", sample.getTimestamp(), definition.getKey());
                }
            }
        }

        private JEVisObject getCalculationObject() {
            if (calculationObject == null && calculationId != -1) {
                try {
                    calculationObject = ds.getObject(calculationId);
                } catch (Exception e) {
                    logger.error("Could not get object for id {}", calculationId, e);
                }
            }
            return calculationObject;
        }

        /**
         * Port of the arithmetic of {@code XYChartSerie.updateTableEntry} (values of the table below a chart).
         */
        RowStatistics statistics(List<ChartSample> samples) {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            double sum = 0d;
            int numeric = 0;
            for (ChartSample sample : samples) {
                try {
                    double currentValue = sample.getDouble();
                    min = Math.min(min, currentValue);
                    max = Math.max(max, currentValue);
                    sum += currentValue;
                    numeric++;
                } catch (Exception ex) {
                    logger.debug("Not a number: {}", sample);
                }
            }
            if (numeric == 0) return null;

            RowStatistics stats = new RowStatistics();
            stats.setCount(samples.size());
            stats.setMin(min);
            stats.setMax(max);

            Double avg = null;
            if (manipulation != ManipulationMode.CUMULATE) {
                avg = sum / samples.size();
            }
            stats.setAvg(avg);

            if (calculation) {
                stats.setEnpi(avg);
                stats.setAvg(calculationAverage(samples.get(0).getTimestamp(), samples.get(samples.size() - 1).getTimestamp()));
            }

            QuantityUnits qu = new QuantityUnits();
            if (qu.isQuantityUnit(unit)) {
                stats.setSum(sum);
                stats.setSumUnit(UnitManager.getInstance().format(unit));
            } else if (qu.isSumCalculable(unit) && manipulation == ManipulationMode.NONE) {
                try {
                    JEVisUnit sumUnit = qu.getSumUnit(unit);
                    ChartUnits cu = new ChartUnits();
                    Period currentPeriod = new Period(samples.get(0).getTimestamp(), samples.get(1).getTimestamp());
                    Period rawPeriod = CleanDataObject.getPeriodForDate(attribute.getObject(), samples.get(0).getTimestamp());
                    double newScaleFactor = cu.scaleValue(rawPeriod, unit.toString(), currentPeriod, sumUnit.toString());
                    stats.setSum(sum * newScaleFactor);
                    stats.setSumUnit(String.valueOf(sumUnit));
                } catch (Exception e) {
                    logger.debug("Couldn't calculate sum of row {}", definition.getKey(), e);
                }
            }
            return stats;
        }

        /**
         * {@code XYChartSerie.updateTableEntry} for calculations: the calculation in absolute mode from the first to the
         * last sample.
         */
        private Double calculationAverage(DateTime first, DateTime last) {
            JEVisObject calculationObject = getCalculationObject();
            if (calculationObject == null) return null;
            try {
                CalcJob calcJob = new CalcJobFactory().getCalcJobForTimeFrame(new SampleHandler(), ds, calculationObject, first, last, true);
                List<JEVisSample> results = calcJob.getResults();
                if (results.size() == 1) {
                    return results.get(0).getValueAsDouble();
                }
            } catch (Exception e) {
                logger.error("Couldn't get calculation result of row {}", definition.getKey(), e);
            }
            return null;
        }

        /**
         * Port of the value of a pie piece in {@code PieChart.init()}.
         */
        Double pieValue(List<ChartSample> samples) {
            QuantityUnits qu = new QuantityUnits();
            boolean isQuantity = qu.isQuantityUnit(unit);
            boolean isSummable = qu.isSumCalculable(unit);

            double factor = 1d;
            if (!isQuantity && isSummable) {
                try {
                    JEVisUnit sumUnit = qu.getSumUnit(unit);
                    String outputUnit = UnitManager.getInstance().format(sumUnit).replace("·", "");
                    if (outputUnit.isEmpty()) outputUnit = sumUnit.getLabel();
                    String inputUnit = UnitManager.getInstance().format(unit).replace("·", "");
                    if (inputUnit.isEmpty()) inputUnit = unit.getLabel();

                    Period currentPeriod = new Period(samples.get(0).getTimestamp(), samples.get(1).getTimestamp());
                    Period rawPeriod = CleanDataObject.getPeriodForDate(attribute.getObject(), samples.get(0).getTimestamp());
                    factor = new ChartUnits().scaleValue(rawPeriod, inputUnit, currentPeriod, outputUnit);
                } catch (Exception e) {
                    logger.error("Could not scale pie value of row {}", definition.getKey(), e);
                    factor = 1d;
                }
            }

            double sumPiePiece = 0d;
            for (ChartSample sample : samples) {
                try {
                    sumPiePiece += sample.getDouble() * factor;
                } catch (Exception e) {
                    logger.error("Not a number: {}", sample);
                }
            }
            if (!isQuantity && !isSummable) {
                sumPiePiece = sumPiePiece / samples.size();
            }
            return sumPiePiece;
        }
    }
}
