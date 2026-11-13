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
package org.jevis.commons.trc;

import com.fasterxml.jackson.databind.JsonNode;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests of the shared result calculation with a Mockito data source. Neutral names only.
 */
public class ResultCalculatorTest {
    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final DateTime NOW = new DateTime(2026, 11, 15, 12, 0, ZONE);
    private static final DateTime START = new DateTime(2026, 11, 1, 0, 0, ZONE);
    private static final DateTime END = new DateTime(2026, 11, 30, 23, 59, 59, 999, ZONE);

    private JEVisDataSource ds;
    private TrcTimeFrames timeFrames;

    @Before
    public void setUp() {
        ds = mock(JEVisDataSource.class);
        timeFrames = new TrcTimeFrames(ZONE, NOW);
    }

    // ---------------------------------------------------------------- fakes

    /**
     * An object with one attribute whose samples are answered by time frame.
     */
    private JEVisAttribute object(long id, String name, String attribute, JEVisSample... samples) throws Exception {
        JEVisObject object = mock(JEVisObject.class);
        JEVisAttribute att = mock(JEVisAttribute.class);
        List<JEVisSample> list = new ArrayList<>(Arrays.asList(samples));
        when(object.getID()).thenReturn(id);
        when(object.getName()).thenReturn(name);
        when(object.getJEVisClassName()).thenReturn("Data");
        when(object.getAttribute(attribute)).thenReturn(att);
        when(att.getObject()).thenReturn(object);
        when(att.getName()).thenReturn(attribute);
        when(att.getSamples(any(DateTime.class), any(DateTime.class))).thenAnswer(invocation -> {
            DateTime from = invocation.getArgument(0);
            DateTime to = invocation.getArgument(1);
            List<JEVisSample> result = new ArrayList<>();
            for (JEVisSample sample : list) {
                if (!sample.getTimestamp().isBefore(from) && !sample.getTimestamp().isAfter(to)) result.add(sample);
            }
            return result;
        });
        when(att.getLatestSample()).thenReturn(list.isEmpty() ? null : list.get(list.size() - 1));
        when(att.hasSample()).thenReturn(!list.isEmpty());
        when(ds.getObject(id)).thenReturn(object);
        return att;
    }

    private static JEVisSample sample(DateTime ts, double value) {
        return new VirtualSample(ts, value);
    }

    private static JEVisSample text(DateTime ts, String value) {
        return new VirtualSample(ts, value);
    }

    private static TemplateInput input(String id, String variable, String type, long objectId) {
        TemplateInput input = new TemplateInput();
        input.setId(id);
        input.setVariableName(variable);
        input.setVariableType(type);
        input.setAttributeName("Value");
        input.setObjectClass("Data");
        input.setObjectID(objectId);
        return input;
    }

    private static TemplateFormula formula(String id, String name, String expression, String output, String... inputIds) {
        TemplateFormula formula = new TemplateFormula();
        formula.setId(id);
        formula.setName(name);
        formula.setFormula(expression);
        formula.setOutput(output);
        formula.setInputIds(new ArrayList<>(Arrays.asList(inputIds)));
        return formula;
    }

    private static TemplateOutput output(String id, String name, String unit, int row) {
        TemplateOutput output = new TemplateOutput();
        output.setId(id);
        output.setName(name);
        output.setUnit(unit);
        output.setRow(row);
        output.setColumn(0);
        return output;
    }

    private static RCTemplate template(List<TemplateInput> inputs, List<TemplateFormula> formulas, List<TemplateOutput> outputs) {
        RCTemplate template = new RCTemplate();
        template.setTemplateInputs(new ArrayList<>(inputs));
        template.setTemplateFormulas(new ArrayList<>(formulas));
        template.setTemplateOutputs(new ArrayList<>(outputs));
        template.getIntervalSelectorConfiguration().put("P1M", true);
        template.getIntervalSelectorConfiguration().put("P1Y", true);
        return template;
    }

    private TrcResult calculate(RCTemplate template) {
        return new ResultCalculator(ds, timeFrames).calculate(template, START, END);
    }

    private static double value(TrcResult result, String outputId) {
        TrcResult.Cell cell = result.cell(outputId);
        assertNotNull(outputId, cell);
        assertNotNull(outputId + " has a value", cell.getValue());
        return cell.getValue();
    }

    // ---------------------------------------------------------------- stored format

    private static final String TEMPLATE_JSON = "{\n" +
            "  \"templateInputs\" : [ {\n" +
            "    \"objectClass\" : \"Clean Data\", \"id\" : \"in-1\", \"quantity\" : true, \"attributeName\" : \"Value\",\n" +
            "    \"variableName\" : \"consumption\", \"variableType\" : \"SUM\", \"templateFormula\" : null,\n" +
            "    \"dependency\" : null, \"filter\" : \"Meter\", \"group\" : false, \"timeRestrictionEnabled\" : false,\n" +
            "    \"fixedTimeFrame\" : null, \"reducingTimeFrame\" : null\n" +
            "  }, {\n" +
            "    \"objectClass\" : \"Energy Supplier\", \"id\" : \"in-2\", \"quantity\" : false, \"attributeName\" : \"Energy Price\",\n" +
            "    \"variableName\" : \"price\", \"variableType\" : \"NON_PERIODIC\", \"templateFormula\" : null,\n" +
            "    \"dependency\" : null, \"filter\" : null, \"group\" : null, \"timeRestrictionEnabled\" : false,\n" +
            "    \"fixedTimeFrame\" : null, \"reducingTimeFrame\" : null\n" +
            "  }, {\n" +
            "    \"id\" : \"old\", \"variableName\" : \"Old formula\", \"variableType\" : \"FORMULA\"\n" +
            "  } ],\n" +
            "  \"templateOutputs\" : [ {\n" +
            "    \"id\" : \"out-1\", \"name\" : \"Energy cost\", \"nameBold\" : false, \"variableName\" : \"cost\", \"tooltip\" : \"Hint\",\n" +
            "    \"showTooltip\" : true, \"resultBold\" : true, \"unit\" : \"€\", \"showLabel\" : true, \"showAnalysisLink\" : false,\n" +
            "    \"link\" : false, \"column\" : 0, \"row\" : 2, \"colSpan\" : 2, \"rowSpan\" : 1, \"separator\" : false\n" +
            "  }, {\n" +
            "    \"id\" : \"out-2\", \"name\" : null, \"column\" : 0, \"row\" : 1, \"colSpan\" : 3, \"rowSpan\" : 1, \"separator\" : true\n" +
            "  } ],\n" +
            "  \"templateFormulas\" : [ {\n" +
            "    \"id\" : \"f-1\", \"name\" : \"Cost\", \"formula\" : \"consumption * price\", \"output\" : \"out-1\",\n" +
            "    \"timeRestrictionEnabled\" : false, \"fixedTimeFrame\" : null, \"reducingTimeFrame\" : null,\n" +
            "    \"inputIds\" : [ \"in-1\", \"in-2\", \"gone\" ]\n" +
            "  } ],\n" +
            "  \"intervalSelectorConfiguration\" : { \"P1D\" : false, \"P1M\" : true, \"P1Y\" : true, \"CUSTOM\" : true },\n" +
            "  \"type\" : \"SimpleDataHandler\"\n" +
            "}";

    private static final String CONFIGURATION_JSON = "{\n" +
            "  \"selectedInputs\" : [ { \"objectClass\" : \"Clean Data\", \"id\" : \"in-1\", \"attributeName\" : \"Value\",\n" +
            "    \"variableName\" : \"consumption\", \"variableType\" : \"SUM\", \"filter\" : \"Meter\", \"group\" : false, \"objectID\" : 101 },\n" +
            "    { \"objectClass\" : \"Energy Supplier\", \"id\" : \"in-2\", \"attributeName\" : \"Energy Price\",\n" +
            "    \"variableName\" : \"price\", \"variableType\" : \"NON_PERIODIC\", \"filter\" : null, \"group\" : null, \"objectID\" : 102 } ],\n" +
            "  \"linkedOutputs\" : [ ],\n" +
            "  \"templateSelection\" : 200,\n" +
            "  \"type\" : \"SimpleDataHandler\",\n" +
            "  \"contractNumber\" : \"C-1\", \"contractType\" : \"ELECTRICITY\", \"marketLocationNumber\" : \"ML-1\",\n" +
            "  \"contractDate\" : null, \"firstRate\" : null, \"periodOfNotice\" : null,\n" +
            "  \"contractStart\" : \"2026-01-01T00:00:00.000+01:00\", \"contractEnd\" : null\n" +
            "}";

    @Test
    public void parsesStoredTemplateAndConfiguration() throws Exception {
        RCTemplate template = TemplateHandler.parse(TEMPLATE_JSON.getBytes(StandardCharsets.UTF_8));
        assertNotNull(template);
        assertEquals("old style formula inputs are removed", 2, template.getTemplateInputs().size());
        assertEquals(1, template.getTemplateFormulaInputs().size());
        assertEquals("Cost", template.getTemplateFormulaInputs().get(0).getVariableName());
        assertEquals("unknown input ids are removed", Arrays.asList("in-1", "in-2"), template.getTemplateFormulas().get(0).getInputIds());
        assertTrue(template.getTemplateInputs().get(0).isQuantity());
        assertEquals("Meter", template.getTemplateInputs().get(0).getFilter());
        assertEquals(Long.valueOf(-1L), template.getTemplateInputs().get(0).getObjectID());
        assertEquals(Arrays.asList("P1M", "P1Y", "CUSTOM"), TrcTimeFrames.enabled(template));
        TemplateOutput out = template.getTemplateOutputs().get(0);
        assertEquals(Integer.valueOf(2), out.getColSpan());
        assertTrue(out.getShowTooltip());

        SelectionTemplate selection = AccountingTemplateHandler.parse(CONFIGURATION_JSON.getBytes(StandardCharsets.UTF_8));
        assertNotNull(selection);
        assertEquals(Long.valueOf(200), selection.getTemplateSelection());
        assertEquals("ML-1", selection.getMarketLocationNumber());
        Map<TemplateInput, TemplateInput> translation = new HashMap<>();
        ResultCalculator.link(template, selection, translation);
        assertEquals(Long.valueOf(101), template.getTemplateInputs().get(0).getObjectID());
        assertEquals(Long.valueOf(102), template.getTemplateInputs().get(1).getObjectID());
        assertEquals(2, translation.size());

        /* written again in the same format */
        TemplateHandler handler = new TemplateHandler();
        handler.setRcTemplate(template);
        JsonNode node = handler.toJsonNode();
        assertEquals("consumption * price", node.get("templateFormulas").get(0).get("formula").asText());
        assertTrue(node.get("intervalSelectorConfiguration").get("P1M").asBoolean());
        RCTemplate again = TemplateHandler.parse(node.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(2, again.getTemplateInputs().size());

        /* calculation of the stored template: 100 kWh * 0.3 */
        object(101, "Meter", "Value", sample(START.plusDays(1), 60), sample(START.plusDays(2), 40));
        object(102, "Supplier", "Energy Price", sample(new DateTime(2026, 1, 1, 0, 0, ZONE), 0.3));
        TrcResult result = calculate(template);
        assertEquals(30d, value(result, "out-1"), 1e-9);
        assertEquals(TrcResult.Kind.SEPARATOR, result.cell("out-2").getKind());
        assertEquals("horizontal", result.cell("out-2").getOrientation());
        assertEquals("Hint", result.cell("out-1").getTooltip());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    public void nullListsInTheJsonAreEmpty() {
        RCTemplate template = TemplateHandler.parse("{\"templateInputs\":null,\"templateFormulas\":[{\"id\":\"f\",\"inputIds\":null}]}"
                .getBytes(StandardCharsets.UTF_8));
        assertNotNull(template);
        assertTrue(template.getTemplateInputs().isEmpty());
        assertTrue(template.getTemplateFormulas().get(0).getInputIds().isEmpty());
        assertNull(TemplateHandler.parse("{broken".getBytes(StandardCharsets.UTF_8)));
    }

    // ---------------------------------------------------------------- input types

    @Test
    public void periodicInputTypes() throws Exception {
        object(1, "Meter", "Value", sample(START.plusDays(1), 10), sample(START.plusDays(2), 30),
                sample(START.plusDays(3), 20), sample(START.minusDays(1), 1000));
        TemplateInput sum = input("s", "s", "SUM", 1);
        sum.setQuantity(true);
        TemplateInput sumNoQuantity = input("sn", "sn", "SUM", 1);
        TemplateInput avg = input("a", "a", "AVG", 1);
        avg.setQuantity(true);
        TemplateInput min = input("mi", "mi", "MIN", 1);
        TemplateInput max = input("ma", "ma", "MAX", 1);
        RCTemplate template = template(Arrays.asList(sum, sumNoQuantity, avg, min, max),
                Arrays.asList(formula("f1", "F1", "s", "o1", "s"), formula("f2", "F2", "sn", "o2", "sn"),
                        formula("f3", "F3", "a", "o3", "a"), formula("f4", "F4", "mi", "o4", "mi"),
                        formula("f5", "F5", "ma", "o5", "ma")),
                Arrays.asList(output("o1", "Sum", "kWh", 0), output("o2", "Sum without quantity", "kWh", 1),
                        output("o3", "Average", "kWh", 2), output("o4", "Min", "kWh", 3), output("o5", "Max", "kWh", 4)));
        TrcResult result = calculate(template);
        assertEquals(60d, value(result, "o1"), 1e-9);
        assertEquals("a SUM input without quantity is averaged", 20d, value(result, "o2"), 1e-9);
        assertEquals(20d, value(result, "o3"), 1e-9);
        assertEquals(10d, value(result, "o4"), 1e-9);
        assertEquals(30d, value(result, "o5"), 1e-9);
    }

    @Test
    public void lastNonPeriodicAndYearlyInputs() throws Exception {
        object(1, "Counter", "Value", sample(START.plusDays(3), 5), sample(START.plusDays(20), 7));
        object(2, "Price", "Value", sample(new DateTime(2026, 6, 1, 0, 0, ZONE), 0.2),
                sample(new DateTime(2026, 10, 1, 0, 0, ZONE), 0.25), sample(START.plusDays(10), 0.5));
        object(3, "Yearly", "Value", sample(new DateTime(2026, 3, 1, 0, 0, ZONE), 3650));
        RCTemplate template = template(Arrays.asList(input("l", "last", "LAST", 1), input("n", "price", "NON_PERIODIC", 2),
                        input("y", "year", "YEARLY_VALUE", 3)),
                Arrays.asList(formula("f1", "F1", "last", "o1", "l"), formula("f2", "F2", "price", "o2", "n"),
                        formula("f3", "F3", "year", "o3", "y")),
                Arrays.asList(output("o1", "Last", "kWh", 0), output("o2", "Price", "€/kWh", 1), output("o3", "Yearly", "€", 2)));
        TrcResult result = calculate(template);
        assertEquals("latest value", 7d, value(result, "o1"), 1e-9);
        assertEquals("last value at or before the start", 0.25, value(result, "o2"), 1e-9);
        /* 3650 / 365 days * 30 days of November (the old Control Center counted 29) */
        assertEquals(300d, value(result, "o3"), 1e-9);
    }

    @Test
    public void textAndNameInputs() throws Exception {
        object(1, "Contract object", "Tariff", text(START.minusDays(5), "Tariff A"));
        TemplateInput tariff = input("t", "tariff", "STRING", 1);
        tariff.setAttributeName("Tariff");
        TemplateInput name = input("n", "object", "STRING", 1);
        name.setAttributeName(ResultCalculator.NAME_ATTRIBUTE);
        RCTemplate template = template(Arrays.asList(tariff, name),
                Collections.singletonList(formula("f1", "Text", "object: tariff", "o1", "t", "n")),
                Collections.singletonList(output("o1", "Contract", null, 0)));
        TrcResult result = calculate(template);
        TrcResult.Cell cell = result.cell("o1");
        assertEquals(TrcResult.Kind.TEXT, cell.getKind());
        assertEquals("Contract object: Tariff A", cell.getText());
        assertNull(cell.getValue());
        assertEquals("text formulas count as 0 for the others", 0d, result.getFormulaResults().get("f1"), 0d);
    }

    @Test
    public void rangingValueByItsDependency() throws Exception {
        object(1, "Peak", "Value", sample(START.plusDays(2), 150));
        object(2, "Grid", "Demand Charge", text(START.minusMonths(2),
                "{\"rangingValues\":[{\"from\":0,\"to\":100,\"value\":5},{\"from\":100,\"to\":1000,\"value\":4}]}"));
        TemplateInput peak = input("p", "peak", "MAX", 1);
        TemplateInput charge = input("c", "charge", "RANGING_VALUE", 2);
        charge.setAttributeName("Demand Charge");
        charge.setDependency("p");
        RCTemplate template = template(Arrays.asList(peak, charge),
                Collections.singletonList(formula("f1", "Demand", "peak * charge", "o1", "p", "c")),
                Collections.singletonList(output("o1", "Demand cost", "€", 0)));
        assertEquals(600d, value(calculate(template), "o1"), 1e-9);
    }

    // ---------------------------------------------------------------- formulas

    @Test
    public void dependentFormulasAreCalculatedFirst() throws Exception {
        object(1, "Meter", "Value", sample(START.plusDays(1), 100));
        TemplateInput consumption = input("c", "consumption", "SUM", 1);
        consumption.setQuantity(true);
        /* gross needs net needs energy; listed in reverse order; "helper" has no output */
        RCTemplate template = template(Collections.singletonList(consumption),
                Arrays.asList(formula("g", "gross", "net * 1.19", "o-gross"),
                        formula("n", "net", "energy + helper", "o-net"),
                        formula("e", "energy", "consumption * 0.3", "o-energy", "c"),
                        formula("h", "helper", "5", "")),
                Arrays.asList(output("o-gross", "Gross", "€", 2), output("o-net", "Net", "€", 1),
                        output("o-energy", "Energy", "€", 0)));
        template.getTemplateFormulas().get(0).getInputIds().add("n");
        template.getTemplateFormulas().get(1).getInputIds().addAll(Arrays.asList("e", "h"));
        TrcResult result = calculate(template);
        assertEquals(30d, value(result, "o-energy"), 1e-9);
        assertEquals(35d, value(result, "o-net"), 1e-9);
        assertEquals(41.65, value(result, "o-gross"), 1e-9);
    }

    @Test
    public void formulaCycleHasNoResult() {
        RCTemplate template = template(Collections.<TemplateInput>emptyList(),
                Arrays.asList(formula("a", "first", "second + 1", "o1", "b"), formula("b", "second", "first + 1", "o2", "a")),
                Arrays.asList(output("o1", "First", "€", 0), output("o2", "Second", "€", 1)));
        TrcResult result = calculate(template);
        assertTrue(result.getWarnings().toString(), result.getWarnings().contains(
                new TrcResult.Warning(TrcResult.WarningType.FORMULA_CYCLE, "first", null)));
    }

    @Test
    public void allInputsAreSubstitutedPerTimeStep() throws Exception {
        object(1, "Meter", "Value", sample(START.plusDays(1), 60), sample(START.plusDays(5), 40));
        object(2, "Price", "Value", sample(new DateTime(2026, 9, 1, 0, 0, ZONE), 0.3));
        TemplateInput consumption = input("c", "consumption", "SUM", 1);
        consumption.setQuantity(true);
        TemplateInput price = input("p", "price", "NON_PERIODIC", 2);
        /* the dependency adds the start of the time frame as time step */
        price.setDependency("c");
        RCTemplate template = template(Arrays.asList(consumption, price),
                Collections.singletonList(formula("f", "Cost", "consumption * price", "o1", "c", "p")),
                Collections.singletonList(output("o1", "Cost", "€", 0)));
        TrcResult result = calculate(template);
        assertEquals(30d, value(result, "o1"), 1e-9);

        /* the old Control Center replaced only the last input per time step: that expression cannot be evaluated */
        Map<String, String> onlyLast = new HashMap<>();
        onlyLast.put("price", "0.3");
        assertNull(ResultCalculator.eval(ResultCalculator.substitute("consumption * price", onlyLast)));
        Map<String, String> all = new HashMap<>(onlyLast);
        all.put("consumption", "100.0");
        assertEquals(30d, ResultCalculator.eval(ResultCalculator.substitute("consumption * price", all)), 1e-9);
    }

    @Test
    public void wholeVariableNamesOnly() throws Exception {
        object(1, "First", "Value", sample(START.plusDays(1), 2));
        object(2, "Second", "Value", sample(START.plusDays(1), 3));
        TemplateInput a = input("a", "a", "SUM", 1);
        a.setQuantity(true);
        TemplateInput ab = input("ab", "ab", "SUM", 2);
        ab.setQuantity(true);
        RCTemplate template = template(Arrays.asList(a, ab),
                Collections.singletonList(formula("f", "Sum", "ab + a * 10", "o1", "a", "ab")),
                Collections.singletonList(output("o1", "Sum", "kWh", 0)));
        /* the old String.replace of "a" turned "ab" into "2.0b" */
        assertEquals(23d, value(calculate(template), "o1"), 1e-9);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("a", "1");
        values.put("a_b", "2");
        values.put("Total net", "3");
        assertEquals("2 + 1 + (3) + ab + xa", ResultCalculator.substitute("a_b + a + (Total net) + ab + xa", values));
    }

    @Test
    public void quantityUnitsAreSummedOtherUnitsAveraged() throws Exception {
        /* yearly value spread over three time steps (start of the year, price sample, start of the time frame) */
        object(1, "Yearly", "Value", sample(new DateTime(2026, 2, 1, 0, 0, ZONE), 3650));
        object(2, "Price", "Value", sample(new DateTime(2026, 9, 1, 0, 0, ZONE), 0.5));
        object(3, "Meter", "Value", sample(START.plusDays(1), 1));
        TemplateInput yearly = input("y", "year", "YEARLY_VALUE", 1);
        TemplateInput price = input("p", "price", "NON_PERIODIC", 2);
        price.setDependency("m");
        TemplateInput meter = input("m", "meter", "SUM", 3);
        RCTemplate template = template(Arrays.asList(yearly, price, meter),
                Arrays.asList(formula("f1", "Quantity", "year + price * 0", "o1", "y", "p"),
                        formula("f2", "Other", "year + price * 0", "o2", "y", "p")),
                Arrays.asList(output("o1", "Sum", "kWh", 0), output("o2", "Average", "%", 1)));
        TrcResult result = calculate(template);
        assertEquals("kWh is summed over the time steps", 300d, value(result, "o1"), 1e-9);
        assertEquals("% is averaged", 300d / 3, value(result, "o2"), 1e-9);
    }

    @Test
    public void infiniteIsZeroAndErrorsHaveNoResult() {
        RCTemplate template = template(Collections.<TemplateInput>emptyList(),
                Arrays.asList(formula("f1", "Huge", "10^400", "o1"), formula("f2", "Broken", "1 / 0", "o2"),
                        formula("f3", "Constant", "42.5", "o3"), formula("f4", "Uses broken", "Broken + 1", "o4", "f2")),
                Arrays.asList(output("o1", "Huge", "€", 0), output("o2", "Broken", "€", 1), output("o3", "Constant", "€", 2),
                        output("o4", "Uses broken", "€", 3)));
        TrcResult result = calculate(template);
        assertEquals(0d, value(result, "o1"), 0d);
        assertNull(result.cell("o2").getValue());
        assertTrue(result.getWarnings().contains(new TrcResult.Warning(TrcResult.WarningType.FORMULA_FAILED, "Broken", null)));
        assertEquals(42.5, value(result, "o3"), 0d);
        assertEquals("failed formulas count as 0", 1d, value(result, "o4"), 0d);
    }

    @Test
    public void unreadableObjectsAreNotRead() throws Exception {
        TemplateInput hidden = input("h", "hidden", "SUM", 77);
        TemplateInput unbound = input("u", "unbound", "SUM", -1);
        TemplateOutput out = output("o1", "Value", "kWh", 0);
        out.setShowAnalysisLink(true);
        RCTemplate template = template(Arrays.asList(hidden, unbound),
                Arrays.asList(formula("f", "Hidden", "hidden", "o1", "h"), formula("g", "Unbound", "unbound", "o2", "u")),
                Arrays.asList(out, output("o2", "Unbound", "kWh", 1)));
        TrcResult result = calculate(template);
        verify(ds, atLeastOnce()).getObject(77L);
        assertTrue(result.getWarnings().contains(new TrcResult.Warning(TrcResult.WarningType.INPUT_NOT_READABLE, "Hidden", "hidden")));
        assertTrue(result.getWarnings().contains(new TrcResult.Warning(TrcResult.WarningType.INPUT_NO_OBJECT, "Unbound", "unbound")));
        assertNull("no analysis link to an unreadable object", result.cell("o1").getAnalysisObjectId());
    }

    @Test
    public void timeRestrictionAndAnalysisLink() throws Exception {
        List<JEVisSample> monthly = new ArrayList<>();
        for (int month = 1; month <= 11; month++) monthly.add(sample(new DateTime(2026, month, 1, 0, 0, ZONE), 10));
        object(1, "Meter", "Value", monthly.toArray(new JEVisSample[0]));
        TemplateInput consumption = input("c", "consumption", "SUM", 1);
        consumption.setQuantity(true);
        TemplateFormula restricted = formula("f", "Year until last month", "consumption", "o1", "c");
        restricted.setTimeRestrictionEnabled(true);
        restricted.setFixedTimeFrame("P1Y");
        restricted.setReducingTimeFrame("P1M");
        TemplateFormula notEnabled = formula("g", "Not enabled", "consumption", "o2", "c");
        notEnabled.setTimeRestrictionEnabled(true);
        notEnabled.setFixedTimeFrame("P3Y");
        notEnabled.setReducingTimeFrame("P1M");
        TemplateOutput out = output("o1", "Year", "kWh", 0);
        out.setShowAnalysisLink(true);
        RCTemplate template = template(Collections.singletonList(consumption), Arrays.asList(restricted, notEnabled),
                Arrays.asList(out, output("o2", "Month", "kWh", 1)));
        TrcResult result = calculate(template);
        /* 1 January to 31 October */
        assertEquals(100d, value(result, "o1"), 1e-9);
        assertEquals("restriction with a time frame that is not enabled is ignored", 10d, value(result, "o2"), 1e-9);
        TrcResult.Cell cell = result.cell("o1");
        assertEquals(Long.valueOf(1), cell.getAnalysisObjectId());
        assertEquals("Value", cell.getAnalysisAttribute());
        assertEquals(new DateTime(2026, 1, 1, 0, 0, ZONE).getMillis(), cell.getAnalysisFrom().getMillis());
        assertEquals(new DateTime(2026, 10, 31, 23, 59, 59, 999, ZONE).getMillis(), cell.getAnalysisTo().getMillis());
        assertNull(result.cell("o2").getAnalysisObjectId());
    }

    @Test
    public void wholeDaysOfTheTimeFrame() {
        assertEquals(30, ResultCalculator.daysOfInterval(START, END));
        assertEquals("exclusive end at midnight", 30, ResultCalculator.daysOfInterval(START, new DateTime(2026, 12, 1, 0, 0, ZONE)));
        assertEquals(365, ResultCalculator.daysOfInterval(new DateTime(2026, 1, 1, 0, 0, ZONE),
                new DateTime(2026, 12, 31, 23, 59, 59, 999, ZONE)));
        assertEquals("leap year", 29, ResultCalculator.daysOfInterval(new DateTime(2028, 2, 1, 0, 0, ZONE),
                new DateTime(2028, 2, 29, 23, 59, 59, 999, ZONE)));
        assertEquals("March with the change to summer time", 31, ResultCalculator.daysOfInterval(
                new DateTime(2026, 3, 1, 0, 0, ZONE), new DateTime(2026, 3, 31, 23, 59, 59, 999, ZONE)));
        assertEquals("a started day counts", 1, ResultCalculator.daysOfInterval(START, START.plusHours(6)));
        assertEquals(0, ResultCalculator.daysOfInterval(START, START));
        assertEquals(1200d, ResultCalculator.prorate(14600, START, END), 1e-9);
    }

    @Test
    public void yearlyValueOnlyFromTheYearOfTheTimeFrame() throws Exception {
        /* as in the Control Center: a sample of the previous year is not carried into the next one */
        object(1, "Yearly", "Value", sample(new DateTime(2025, 1, 1, 0, 0, ZONE), 3650));
        object(2, "Yearly 2", "Value", sample(new DateTime(2025, 1, 1, 0, 0, ZONE), 3650),
                sample(new DateTime(2026, 1, 1, 0, 0, ZONE), 7300), sample(new DateTime(2027, 1, 1, 0, 0, ZONE), 1));
        RCTemplate template = template(Arrays.asList(input("y", "year", "YEARLY_VALUE", 1), input("z", "year2", "YEARLY_VALUE", 2)),
                Arrays.asList(formula("f1", "F1", "year", "o1", "y"), formula("f2", "F2", "year2", "o2", "z")),
                Arrays.asList(output("o1", "Old year only", "€", 0), output("o2", "This year", "€", 1)));
        TrcResult result = calculate(template);
        assertTrue(result.getWarnings().contains(new TrcResult.Warning(TrcResult.WarningType.INPUT_NO_VALUES, "F1", "year")));
        assertEquals("the sample of 2026 prorated to November", 600d, value(result, "o2"), 1e-9);
    }

    @Test
    public void fixedTimeFrameWithoutReduction() throws Exception {
        /* peak of the year up to the end of the requested month: fixed "P1Y", reducing "NONE" */
        object(1, "Meter", "Value", sample(new DateTime(2026, 2, 3, 10, 0, ZONE), 30),
                sample(new DateTime(2026, 11, 2, 10, 0, ZONE), 20), sample(new DateTime(2026, 11, 30, 23, 45, ZONE), 25));
        TemplateInput peak = input("p", "peak", "MAX", 1);
        TemplateFormula year = formula("f1", "Peak of the year", "peak * 4", "o1", "p");
        year.setTimeRestrictionEnabled(true);
        year.setFixedTimeFrame("P1Y");
        year.setReducingTimeFrame(TrcTimeFrames.NONE);
        TemplateFormula month = formula("f2", "Peak of the month", "peak * 4", "o2", "p");
        TemplateInput restrictedPeak = input("q", "restricted", "MAX", 1);
        restrictedPeak.setTimeRestrictionEnabled(true);
        restrictedPeak.setFixedTimeFrame("P1Y");
        restrictedPeak.setReducingTimeFrame(TrcTimeFrames.NONE);
        TemplateFormula input = formula("f3", "Restricted input", "restricted", "o3", "q");
        RCTemplate template = template(Arrays.asList(peak, restrictedPeak), Arrays.asList(year, month, input),
                Arrays.asList(output("o1", "Year", "kW", 0), output("o2", "Month", "kW", 1), output("o3", "Input", "kWh", 2)));
        TrcResult result = calculate(template);
        assertEquals(120d, value(result, "o1"), 1e-9);
        assertEquals("the last quarter hour of the month is included", 100d, value(result, "o2"), 1e-9);
        assertEquals(30d, value(result, "o3"), 1e-9);
    }

    @Test
    public void timeFrames() {
        assertEquals(Arrays.asList("plugin.object.report.dialog.period.last", "plugin.object.report.dialog.aggregation.threeyears"),
                TrcTimeFrames.nameKeys("P3Y"));
        assertEquals(new DateTime(2026, 11, 1, 0, 0, ZONE).getMillis(), timeFrames.interval("P1M", NOW).getStart().getMillis());
        assertEquals(new DateTime(2026, 11, 15, 0, 0, ZONE).getMillis(), timeFrames.interval("CUSTOM", NOW).getStart().getMillis());
        assertNull(timeFrames.interval(TrcTimeFrames.NONE, NOW));
    }
}
