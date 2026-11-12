/**
 * Copyright (C) JEVis contributors
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
package org.jevis.commons.trc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.calculation.CalcExpressions;
import org.jevis.commons.chart.TimeRange;
import org.jevis.commons.datetime.PeriodHelper;
import org.jevis.commons.object.plugin.RangingValues;
import org.jevis.commons.unit.ChartUnits.QuantityUnits;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Period;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates a result calculation template ({@link RCTemplate}) for a time frame: the calculation of the Control
 * Center's {@code OutputView} ("Template Result Calculation" and the view of the "Accounting Plugin"), ported from
 * {@code OutputView.buildOutput} and {@code TemplateInput.CreateValues/getValue}. Shared by the Control Center and the
 * web service.
 * <p>
 * The objects of the inputs must be set ({@link TemplateInput#getObjectID()}, see
 * {@link #link(RCTemplate, SelectionTemplate, Map)}). Objects the data source does not return (missing or not readable)
 * are never read; the input counts as having no values and a warning is added.
 *
 * <h2>Per formula</h2>
 * <ol>
 *     <li>Time frame: the requested one, or with a time restriction (fixed and reducing time frame, both among the time
 *     frames enabled in the template) from the start of the fixed time frame to the end reduced by the reducing time
 *     frame.</li>
 *     <li>Inputs (in the order of the template): {@code STRING} inputs and the attribute {@code Name} make the formula a
 *     text formula; {@code SUM/AVG/MIN/MAX} are aggregated over the time frame (each with its own time restriction) and
 *     have one value at its start; {@code NON_PERIODIC} has the last value at or before the start (its dependency adds
 *     time steps); {@code LAST} the latest value; {@code YEARLY_VALUE} the last value of the year of the start,
 *     prorated to the days of the time frame; {@code RANGING_VALUE} the value of the ranging table for the value of its
 *     dependency. The time steps are all time stamps of these values; {@code NON_PERIODIC} is carried forward,
 *     {@code LAST} repeated and {@code YEARLY_VALUE} spread (divided by the number of time steps).</li>
 *     <li>Per time step at which all inputs have a value, all inputs and the results of the formulas used as inputs
 *     are substituted (whole variable names only) and the formula is evaluated ({@link CalcExpressions}). The results
 *     are summed if the unit of the output is a quantity unit, else averaged.</li>
 *     <li>If no time step could be evaluated or the result is {@code 0}, the formula is evaluated once with the first
 *     value of every input (the old behaviour).</li>
 *     <li>NaN/infinite results are {@code 0}; formulas that cannot be evaluated have no result ({@code 0} for the
 *     formulas using them).</li>
 * </ol>
 * The old Control Center substituted only the last input per time step (so the time steps failed and the first
 * values were used) and replaced variable names inside longer names.
 */
public class ResultCalculator {
    /**
     * Attribute name of the "name" inputs ({@code JEVisNameType})
     */
    public static final String NAME_ATTRIBUTE = "name";
    /**
     * Attribute name that makes a formula a text formula (as the Control Center checks it)
     */
    static final String TEXT_NAME_ATTRIBUTE = "Name";
    private static final Logger logger = LogManager.getLogger(ResultCalculator.class);
    private static final String IDENTIFIER_CHAR = "[\\p{L}\\p{N}_]";

    private final JEVisDataSource ds;
    private final TrcTimeFrames timeFrames;
    private final QuantityUnits quantityUnits = new QuantityUnits();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    /**
     * @param ds         data source with the rights of the user
     * @param timeFrames time zone and "now" for the time restrictions
     */
    public ResultCalculator(JEVisDataSource ds, TrcTimeFrames timeFrames) {
        this.ds = ds;
        this.timeFrames = timeFrames != null ? timeFrames : new TrcTimeFrames(null, null);
    }

    /**
     * Binds the inputs of the template to the objects of an accounting configuration
     * ({@code OutputView.linkConfiguredToTemplate}): inputs of the template that the configuration does not have are
     * added to it; every template input gets the object of the configured input with the same id.
     *
     * @param translation filled with template input to configured input, may be {@code null}
     */
    public static void link(RCTemplate template, SelectionTemplate selection, Map<TemplateInput, TemplateInput> translation) {
        if (template == null || selection == null) return;
        for (TemplateInput templateInput : template.getTemplateInputs()) {
            if (!selection.getSelectedInputs().contains(templateInput)) selection.getSelectedInputs().add(templateInput);
        }
        for (TemplateInput templateInput : template.getTemplateInputs()) {
            for (TemplateInput input : selection.getSelectedInputs()) {
                if (templateInput.equals(input)) {
                    if (translation != null) translation.put(templateInput, input);
                    templateInput.setObjectID(input.getObjectID());
                    break;
                }
            }
        }
        for (TemplateOutput templateOutput : template.getTemplateOutputs()) {
            if (!selection.getLinkedOutputs().contains(templateOutput)) selection.getLinkedOutputs().add(templateOutput);
        }
        for (TemplateOutput templateOutput : template.getTemplateOutputs()) {
            for (TemplateOutput output : selection.getLinkedOutputs()) {
                if (templateOutput.equals(output)) {
                    templateOutput.setTarget(output.getTarget());
                    break;
                }
            }
        }
    }

    /**
     * @return the formula whose output is the given one (the first, as the Control Center), or {@code null}
     */
    public static TemplateFormula formulaOf(RCTemplate template, TemplateOutput output) {
        if (output == null || output.getId() == null) return null;
        for (TemplateFormula formula : template.getTemplateFormulas()) {
            if (output.getId().equals(formula.getOutput())) return formula;
        }
        return null;
    }

    /**
     * @param template the template with the objects of the inputs set
     * @param start    start of the time frame
     * @param end      end of the time frame
     */
    public TrcResult calculate(RCTemplate template, DateTime start, DateTime end) {
        return new Run(template, start, end).execute();
    }

    /**
     * Replaces whole variable names (not preceded or followed by a letter, digit or {@code _}), longest names first,
     * in one pass (substituted values are not substituted again).
     */
    static String substitute(String formula, Map<String, String> values) {
        if (formula == null) return "";
        List<String> names = new ArrayList<>();
        for (String name : values.keySet()) {
            if (name != null && !name.isEmpty()) names.add(name);
        }
        if (names.isEmpty()) return formula;
        names.sort((a, b) -> Integer.compare(b.length(), a.length()));
        StringBuilder alternatives = new StringBuilder();
        for (String name : names) {
            if (alternatives.length() > 0) alternatives.append('|');
            alternatives.append(Pattern.quote(name));
        }
        Pattern pattern = Pattern.compile("(?<!" + IDENTIFIER_CHAR + ")(?:" + alternatives + ")(?!" + IDENTIFIER_CHAR + ")");
        Matcher matcher = pattern.matcher(formula);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(values.get(matcher.group()))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * @return the value of the expression, {@code null} if it cannot be evaluated
     */
    static Double eval(String expression) {
        try {
            return CalcExpressions.create(expression).eval().doubleValue();
        } catch (RuntimeException e) {
            logger.debug("Formula evaluation failed for '{}': {}", expression, e.getMessage());
            return null;
        }
    }

    private static boolean is(String type, InputVariableType expected) {
        return expected.toString().equals(type);
    }

    private static boolean isPeriodic(String type) {
        return is(type, InputVariableType.SUM) || is(type, InputVariableType.AVG)
                || is(type, InputVariableType.MIN) || is(type, InputVariableType.MAX);
    }

    private static Period period(String id) {
        try {
            return new Period(id);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Result of one formula.
     */
    private static final class Outcome {
        final Double value;
        final String text;
        final boolean isText;
        final DateTime from;
        final DateTime to;

        Outcome(Double value, String text, boolean isText, DateTime from, DateTime to) {
            this.value = value;
            this.text = text;
            this.isText = isText;
            this.from = from;
            this.to = to;
        }

        /**
         * @return the value the other formulas use
         */
        double forOthers() {
            return !isText && value != null ? value : 0d;
        }
    }

    private final class Run {
        private final RCTemplate template;
        private final DateTime start;
        private final DateTime end;
        private final TrcResult result;
        private final List<String> enabled;
        private final Map<String, Outcome> outcomes = new HashMap<>();
        private final Set<String> inProgress = new HashSet<>();

        Run(RCTemplate template, DateTime start, DateTime end) {
            this.template = template;
            this.start = start;
            this.end = end;
            this.result = new TrcResult(start, end);
            this.enabled = TrcTimeFrames.enabled(template);
        }

        TrcResult execute() {
            if (template == null) return result;
            for (TemplateOutput output : template.getTemplateOutputs()) {
                if (Boolean.TRUE.equals(output.getSeparator())) {
                    result.getCells().add(new TrcResult.Cell(output, TrcResult.Kind.SEPARATOR));
                    continue;
                }
                TemplateFormula formula = formulaOf(template, output);
                if (formula == null) {
                    result.getCells().add(new TrcResult.Cell(output, TrcResult.Kind.LABEL));
                    continue;
                }
                Outcome outcome = evaluate(formula);
                TrcResult.Cell cell = new TrcResult.Cell(output, outcome.isText ? TrcResult.Kind.TEXT : TrcResult.Kind.VALUE);
                if (outcome.isText) {
                    cell.setText(outcome.text);
                } else {
                    cell.setValue(outcome.value);
                }
                analysisLink(cell, output, formula, outcome);
                result.getCells().add(cell);
            }
            return result;
        }

        /**
         * The analysis button of the Control Center: "show analysis link" and exactly one input (not a formula, not the
         * name), whose object can be read.
         */
        private void analysisLink(TrcResult.Cell cell, TemplateOutput output, TemplateFormula formula, Outcome outcome) {
            if (!Boolean.TRUE.equals(output.getShowAnalysisLink()) || inputIds(formula).size() != 1) return;
            TemplateInput input = input(inputIds(formula).get(0));
            if (input == null || NAME_ATTRIBUTE.equals(input.getAttributeName()) || input.getAttributeName() == null) return;
            Long id = input.getObjectID();
            if (id == null || id <= 0) return;
            try {
                if (ds.getObject(id) == null) return;
            } catch (Exception e) {
                return;
            }
            cell.setAnalysis(id, input.getAttributeName(), outcome.from, outcome.to);
        }

        private List<String> inputIds(TemplateFormula formula) {
            return formula.getInputIds() != null ? formula.getInputIds() : Collections.<String>emptyList();
        }

        private TemplateInput input(String id) {
            if (id == null) return null;
            for (TemplateInput input : template.getTemplateInputs()) {
                if (id.equals(input.getId())) return input;
            }
            return null;
        }

        private TemplateOutput outputOf(TemplateFormula formula) {
            if (formula.getOutput() == null) return null;
            for (TemplateOutput output : template.getTemplateOutputs()) {
                if (formula.getOutput().equals(output.getId())) return output;
            }
            return null;
        }

        private void warn(TrcResult.WarningType type, TemplateFormula formula, TemplateInput input) {
            result.addWarning(new TrcResult.Warning(type, formula != null ? formula.getName() : null,
                    input != null ? input.getVariableName() : null));
        }

        private Outcome evaluate(TemplateFormula formula) {
            String key = formula.getId();
            Outcome known = outcomes.get(key);
            if (known != null) return known;
            if (!inProgress.add(key)) {
                warn(TrcResult.WarningType.FORMULA_CYCLE, formula, null);
                return new Outcome(null, null, false, start, end);
            }
            Outcome outcome;
            try {
                outcome = compute(formula);
            } catch (Exception e) {
                logger.error("Error in formula {}", formula.getName(), e);
                warn(TrcResult.WarningType.FORMULA_FAILED, formula, null);
                outcome = new Outcome(null, null, false, start, end);
            } finally {
                inProgress.remove(key);
            }
            outcomes.put(key, outcome);
            result.getFormulaResults().put(key, outcome.forOthers());
            return outcome;
        }

        private Outcome compute(TemplateFormula formula) {
            DateTime[] range = restrictFormula(formula);
            DateTime from = range[0];
            DateTime to = range[1];
            String expression = formula.getFormula() != null ? formula.getFormula() : "";
            List<String> ids = inputIds(formula);

            boolean isText = false;
            Map<String, String> fixed = new LinkedHashMap<>();
            LinkedHashMap<TemplateInput, Map<DateTime, Double>> stepInputs = new LinkedHashMap<>();
            TreeSet<DateTime> timestamps = new TreeSet<>();

            for (TemplateInput input : template.getTemplateInputs()) {
                if (!ids.contains(input.getId())) continue;
                String type = input.getVariableType();
                if (is(type, InputVariableType.STRING) || TEXT_NAME_ATTRIBUTE.equals(input.getAttributeName())) {
                    isText = true;
                    putIfAbsent(fixed, input.getVariableName(), value(input, formula, from, to, null));
                } else if (isPeriodic(type) || is(type, InputVariableType.LAST) || is(type, InputVariableType.YEARLY_VALUE)) {
                    Map<DateTime, Double> values = createValues(input, formula, from, to);
                    timestamps.addAll(values.keySet());
                    stepInputs.put(input, values);
                } else if (is(type, InputVariableType.NON_PERIODIC)) {
                    Map<DateTime, Double> values = createValues(input, formula, from, to);
                    timestamps.addAll(values.keySet());
                    stepInputs.put(input, values);
                    TemplateInput dependency = input(input.getDependency());
                    if (dependency != null) {
                        timestamps.addAll(createValues(dependency, formula, from, to).keySet());
                    }
                } else if (is(type, InputVariableType.RANGING_VALUE)) {
                    Double determination = null;
                    TemplateInput dependency = input(input.getDependency());
                    if (dependency != null) {
                        try {
                            determination = Double.parseDouble(value(dependency, formula, from, to, null));
                        } catch (NumberFormatException e) {
                            logger.debug("Determination value of {} is no number", input.getVariableName());
                        }
                    }
                    timestamps.add(from);
                    putIfAbsent(fixed, input.getVariableName(), value(input, formula, from, to, determination));
                }
            }

            fill(stepInputs, timestamps);

            for (TemplateFormula other : template.getTemplateFormulas()) {
                if (ids.contains(other.getId())) {
                    putIfAbsent(fixed, other.getName(), String.valueOf(evaluate(other).forOthers()));
                }
            }

            Map<String, String> firstValues = new LinkedHashMap<>(fixed);
            for (Map.Entry<TemplateInput, Map<DateTime, Double>> entry : stepInputs.entrySet()) {
                Iterator<Double> values = entry.getValue().values().iterator();
                if (values.hasNext()) putIfAbsent(firstValues, entry.getKey().getVariableName(), String.valueOf(values.next()));
            }

            if (isText) {
                return new Outcome(null, substitute(expression, firstValues), true, from, to);
            }

            List<Double> stepResults = new ArrayList<>();
            for (DateTime ts : timestamps) {
                Map<String, String> values = new LinkedHashMap<>(fixed);
                boolean complete = true;
                for (Map.Entry<TemplateInput, Map<DateTime, Double>> entry : stepInputs.entrySet()) {
                    Double value = entry.getValue().get(ts);
                    if (value == null) {
                        complete = false;
                        break;
                    }
                    putIfAbsent(values, entry.getKey().getVariableName(), String.valueOf(value));
                }
                if (!complete) continue;
                Double value = eval(substitute(expression, values));
                if (value != null && !value.isNaN()) stepResults.add(value);
            }

            Double calculate = null;
            if (!stepResults.isEmpty()) {
                double sum = 0d;
                for (Double value : stepResults) sum += value;
                TemplateOutput output = outputOf(formula);
                if (output == null || !quantityUnits.isQuantityUnit(output.getUnit())) {
                    sum = sum / stepResults.size();
                }
                calculate = sum;
            }

            if (calculate == null || calculate == 0d) {
                String single = substitute(expression, firstValues);
                Double value = null;
                if (ids.isEmpty()) {
                    try {
                        value = Double.parseDouble(single.trim());
                    } catch (NumberFormatException e) {
                        logger.debug("Formula {} without inputs is no number", formula.getName());
                    }
                }
                if (value == null) value = eval(single);
                if (value != null) calculate = value;
            }

            if (calculate == null) {
                warn(TrcResult.WarningType.FORMULA_FAILED, formula, null);
                return new Outcome(null, null, false, from, to);
            }
            if (calculate.isNaN() || calculate.isInfinite()) calculate = 0d;
            return new Outcome(calculate, null, false, from, to);
        }

        private void putIfAbsent(Map<String, String> map, String name, String value) {
            if (name != null && !name.isEmpty() && !map.containsKey(name)) map.put(name, value);
        }

        /**
         * {@code NON_PERIODIC} carried forward, {@code LAST} repeated, {@code YEARLY_VALUE} spread over the time steps.
         */
        private void fill(Map<TemplateInput, Map<DateTime, Double>> stepInputs, TreeSet<DateTime> timestamps) {
            for (Map.Entry<TemplateInput, Map<DateTime, Double>> entry : stepInputs.entrySet()) {
                String type = entry.getKey().getVariableType();
                Map<DateTime, Double> values = entry.getValue();
                if (values.isEmpty()) continue;
                if (is(type, InputVariableType.NON_PERIODIC)) {
                    Double last = values.values().iterator().next();
                    for (DateTime ts : timestamps) {
                        if (!values.containsKey(ts)) {
                            values.put(ts, last);
                        } else {
                            last = values.get(ts);
                        }
                    }
                } else if (is(type, InputVariableType.LAST)) {
                    Double value = values.values().iterator().next();
                    for (DateTime ts : timestamps) values.put(ts, value);
                } else if (is(type, InputVariableType.YEARLY_VALUE)) {
                    Double value = values.values().iterator().next() / timestamps.size();
                    for (DateTime ts : timestamps) values.put(ts, value);
                }
            }
        }

        // ------------------------------------------------------------ time restrictions

        /**
         * @return {fixed, reducing} time frame ids if both are among the enabled time frames (as the Control Center
         * looks them up in its time frame box), else {@code null}
         */
        private String[] restriction(String fixedId, String reducingId) {
            String fixed = null;
            String reducing = null;
            for (String id : enabled) {
                if (id.equals(fixedId)) {
                    fixed = id;
                } else if (id.equals(reducingId)) {
                    reducing = id;
                }
            }
            return fixed != null && reducing != null ? new String[]{fixed, reducing} : null;
        }

        private DateTime[] restrictFormula(TemplateFormula formula) {
            DateTime from = start;
            DateTime to = end;
            if (!Boolean.TRUE.equals(formula.getTimeRestrictionEnabled())) return new DateTime[]{from, to};
            String[] restriction = restriction(formula.getFixedTimeFrame(), formula.getReducingTimeFrame());
            if (restriction == null) return new DateTime[]{from, to};
            TimeRange fixedRange = timeFrames.interval(restriction[0], start);
            if (fixedRange == null) return new DateTime[]{from, to};
            from = fixedRange.getStart();

            DateTime previousEndDate;
            Period p = period(restriction[1]);
            if (p != null) {
                DateTime minusPeriodToDate = PeriodHelper.minusPeriodToDate(to, p);
                boolean followUp = false;
                if (p.equals(Period.years(1))) {
                    minusPeriodToDate = minusPeriodToDate.withMonthOfYear(12);
                    followUp = true;
                }
                if (followUp || p.equals(Period.months(1))) {
                    minusPeriodToDate = minusPeriodToDate.withDayOfMonth(minusPeriodToDate.dayOfMonth().getMaximumValue());
                    followUp = true;
                }
                if (followUp || p.equals(Period.days(1))) {
                    minusPeriodToDate = minusPeriodToDate.withHourOfDay(23);
                    followUp = true;
                }
                if (followUp || p.equals(Period.hours(1))) {
                    minusPeriodToDate = minusPeriodToDate.withMinuteOfHour(59);
                    followUp = true;
                }
                if (followUp || p.equals(Period.minutes(1))) {
                    minusPeriodToDate = minusPeriodToDate.withSecondOfMinute(59);
                }
                previousEndDate = minusPeriodToDate;
            } else {
                TimeRange reducingRange = timeFrames.interval(restriction[1], start);
                if (reducingRange == null) return new DateTime[]{from, to};
                previousEndDate = to.minus(reducingRange.getEnd().getMillis() - reducingRange.getStart().getMillis());
            }

            if (previousEndDate.isAfter(from)) {
                to = previousEndDate;
            } else {
                from = from.plusSeconds(1);
                to = from;
            }
            return new DateTime[]{from, to};
        }

        private DateTime[] restrictInput(TemplateInput input, DateTime from, DateTime to) {
            if (!Boolean.TRUE.equals(input.getTimeRestrictionEnabled())) return new DateTime[]{from, to};
            String[] restriction = restriction(input.getFixedTimeFrame(), input.getReducingTimeFrame());
            if (restriction == null) return new DateTime[]{from, to};
            TimeRange fixedRange = timeFrames.interval(restriction[0], from);
            if (fixedRange == null) return new DateTime[]{from, to};
            from = fixedRange.getStart();
            DateTime previousEndDate;
            Period p = period(restriction[1]);
            if (p != null) {
                previousEndDate = PeriodHelper.minusPeriodToDate(to, p);
            } else {
                TimeRange reducingRange = timeFrames.interval(restriction[1], from);
                if (reducingRange == null) return new DateTime[]{from, to};
                previousEndDate = to.minus(reducingRange.getEnd().getMillis() - reducingRange.getStart().getMillis());
            }
            if (previousEndDate.isAfter(from)) to = previousEndDate;
            return new DateTime[]{from, to};
        }

        // ------------------------------------------------------------ inputs

        private JEVisObject object(TemplateInput input, TemplateFormula formula) {
            Long id = input.getObjectID();
            if (id == null || id <= 0) {
                warn(TrcResult.WarningType.INPUT_NO_OBJECT, formula, input);
                return null;
            }
            JEVisObject object = null;
            try {
                object = ds.getObject(id);
            } catch (Exception e) {
                logger.debug("Could not get object {}", id, e);
            }
            if (object == null) warn(TrcResult.WarningType.INPUT_NOT_READABLE, formula, input);
            return object;
        }

        private JEVisAttribute attribute(TemplateInput input, TemplateFormula formula) {
            JEVisObject object = object(input, formula);
            if (object == null) return null;
            JEVisAttribute attribute = null;
            try {
                attribute = input.getAttributeName() != null ? object.getAttribute(input.getAttributeName()) : null;
            } catch (Exception e) {
                logger.debug("Could not get attribute {} of {}", input.getAttributeName(), input.getObjectID(), e);
            }
            if (attribute == null) warn(TrcResult.WarningType.INPUT_NO_VALUES, formula, input);
            return attribute;
        }

        /**
         * {@code TemplateInput.CreateValues}: the values of the input by time stamp.
         */
        private Map<DateTime, Double> createValues(TemplateInput input, TemplateFormula formula, DateTime from, DateTime to) {
            Map<DateTime, Double> values = new TreeMap<>();
            DateTime[] range = restrictInput(input, from, to);
            from = range[0];
            to = range[1];
            if (input.getAttributeName() == null || NAME_ATTRIBUTE.equals(input.getAttributeName())) return values;
            JEVisAttribute attribute = attribute(input, formula);
            if (attribute == null) return values;
            String type = input.getVariableType();
            try {
                if (isPeriodic(type)) {
                    List<JEVisSample> samples = attribute.getSamples(from, to);
                    double sum = 0d;
                    double min = Double.MAX_VALUE;
                    double max = -Double.MAX_VALUE;
                    int count = 0;
                    for (JEVisSample sample : samples) {
                        Double d = sample.getValueAsDouble();
                        if (d == null) continue;
                        sum += d;
                        min = Math.min(min, d);
                        max = Math.max(max, d);
                        count++;
                    }
                    if (count == 0) warn(TrcResult.WarningType.INPUT_NO_VALUES, formula, input);
                    if ((!Boolean.TRUE.equals(input.isQuantity()) || is(type, InputVariableType.AVG)) && count > 0) {
                        sum = sum / count;
                    }
                    if (is(type, InputVariableType.AVG) || is(type, InputVariableType.SUM)) {
                        values.put(from, sum);
                    } else if (is(type, InputVariableType.MIN)) {
                        values.put(from, min == Double.MAX_VALUE ? 0d : min);
                    } else {
                        values.put(from, max == -Double.MAX_VALUE ? 0d : max);
                    }
                } else if (is(type, InputVariableType.NON_PERIODIC)) {
                    List<JEVisSample> samples = attribute.getSamples(new DateTime(1990, 1, 1, 0, 0, 0, from.getZone()), to);
                    for (int i = samples.size() - 1; i > -1; i--) {
                        JEVisSample sample = samples.get(i);
                        if (!sample.getTimestamp().isAfter(from)) {
                            values.put(sample.getTimestamp(), sample.getValueAsDouble());
                            break;
                        }
                    }
                } else if (is(type, InputVariableType.LAST)) {
                    JEVisSample sample = attribute.getLatestSample();
                    if (sample != null) values.put(from, sample.getValueAsDouble());
                } else if (is(type, InputVariableType.YEARLY_VALUE)) {
                    DateTime ydt = new DateTime(from.getYear(), 1, 1, 0, 0, 0, 0, from.getZone());
                    List<JEVisSample> samples = attribute.getSamples(ydt, ydt.plusYears(1).minusMillis(1));
                    if (!samples.isEmpty()) {
                        JEVisSample sample = samples.get(samples.size() - 1);
                        LocalDate ld = new LocalDate(from.getYear(), 1, 1);
                        int daysOfYear = Days.daysBetween(ld, ld.plusYears(1)).getDays();
                        int daysOfInterval = Days.daysBetween(from.toLocalDate(), to.toLocalDate()).getDays();
                        values.put(ydt, sample.getValueAsDouble() / daysOfYear * daysOfInterval);
                    }
                }
            } catch (Exception e) {
                logger.error("Could not get template input value for {}", input.getVariableName(), e);
            }
            values.values().removeIf(Objects::isNull);
            if (values.isEmpty() && !isPeriodic(type)) warn(TrcResult.WarningType.INPUT_NO_VALUES, formula, input);
            return values;
        }

        /**
         * {@code TemplateInput.getValue}: the value of the input as text ({@code "0"} if it cannot be read).
         */
        private String value(TemplateInput input, TemplateFormula formula, DateTime from, DateTime to, Double determinationValue) {
            try {
                if (NAME_ATTRIBUTE.equals(input.getAttributeName())) {
                    JEVisObject object = object(input, formula);
                    return object != null ? object.getName() : "0";
                }
                JEVisAttribute attribute = attribute(input, formula);
                if (attribute == null) return "0";
                String type = input.getVariableType();
                if (type == null) return "0";
                String returnValue = "-";
                if (isPeriodic(type)) {
                    boolean quantity = quantityUnits.isQuantityUnit(attribute.getInputUnit());
                    quantity = quantityUnits.isQuantityIfCleanData(attribute, quantity);
                    List<JEVisSample> samples = attribute.getSamples(from, to);
                    double sum = 0d;
                    double min = Double.MAX_VALUE;
                    double max = -Double.MAX_VALUE;
                    for (JEVisSample sample : samples) {
                        Double d = sample.getValueAsDouble();
                        sum += d;
                        min = Math.min(min, d);
                        max = Math.max(max, d);
                    }
                    if (samples.isEmpty()) warn(TrcResult.WarningType.INPUT_NO_VALUES, formula, input);
                    if (!quantity || is(type, InputVariableType.AVG)) sum = sum / samples.size();
                    if (is(type, InputVariableType.AVG) || is(type, InputVariableType.SUM)) {
                        returnValue = String.valueOf(sum);
                    } else if (is(type, InputVariableType.MIN)) {
                        returnValue = String.valueOf(min == Double.MAX_VALUE ? 0d : min);
                    } else {
                        returnValue = String.valueOf(max == -Double.MAX_VALUE ? 0d : max);
                    }
                } else if (is(type, InputVariableType.NON_PERIODIC)) {
                    List<JEVisSample> samples = attribute.getSamples(new DateTime(1990, 1, 1, 0, 0, 0, from.getZone()), from);
                    returnValue = String.valueOf(samples.get(samples.size() - 1).getValueAsDouble());
                } else if (is(type, InputVariableType.LAST)) {
                    returnValue = String.valueOf(attribute.getLatestSample().getValueAsDouble());
                } else if (is(type, InputVariableType.YEARLY_VALUE)) {
                    JEVisSample sample = attribute.getLatestSample();
                    LocalDate ld = new LocalDate(from.getYear(), 1, 1);
                    int daysOfYear = Days.daysBetween(ld, ld.plusYears(1)).getDays();
                    int daysOfInterval = Days.daysBetween(from.toLocalDate(), to.toLocalDate()).getDays();
                    returnValue = String.valueOf(sample.getValueAsDouble() / daysOfYear * daysOfInterval);
                } else if (is(type, InputVariableType.STRING)) {
                    returnValue = attribute.getLatestSample().getValueAsString();
                } else if (is(type, InputVariableType.RANGING_VALUE) && determinationValue != null) {
                    JEVisSample latestSample = attribute.getLatestSample();
                    RangingValues rangingValues = new RangingValues();
                    try {
                        mapper.readerForUpdating(rangingValues).readValue(latestSample.getValueAsString());
                    } catch (Exception e) {
                        logger.error("Could not parse json model", e);
                    }
                    Double value = rangingValues.getValue(determinationValue);
                    if (value != null) returnValue = String.valueOf(value);
                }
                if (returnValue == null || returnValue.equals("NaN") || returnValue.equals("")) returnValue = "0";
                return returnValue;
            } catch (Exception e) {
                logger.debug("Could not get template input value for {}", input.getVariableName(), e);
                warn(TrcResult.WarningType.INPUT_NO_VALUES, formula, input);
            }
            return "0";
        }
    }
}
