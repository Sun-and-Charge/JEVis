/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with JECommons. If not, see
 * <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.calculation;

import com.udojava.evalex.Expression;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class CalcExpressionsTest {

    private static double eval(String formula, Object... vars) {
        Expression e = CalcExpressions.create(formula);
        for (int i = 0; i < vars.length; i += 2) e.setVariable((String) vars[i], new BigDecimal(vars[i + 1].toString()));
        return e.eval().doubleValue();
    }

    /** formula of a demo calculation written for JEval: round with one argument, comparison as 0/1, line breaks */
    @Test
    public void roundWithOneArgumentAndLineBreaks() {
        String f = "round(\n        ( (Strom_Gesamt-50 ) > 0) *( (Strom_Gesamt-50)/25)\n)\n";
        assertEquals(6.0, eval(f, "Strom_Gesamt", 200), 0);   // 150/25
        assertEquals(5.0, eval(f, "Strom_Gesamt", 172), 0);   // 4.88 -> 5
        assertEquals(0.0, eval(f, "Strom_Gesamt", 20), 0);    // below 50: 0
    }

    /** absolute humidity (Magnus formula) as written for JEval with pow() */
    @Test
    public void powAsInJEval() {
        String f = "pow(10,5)*18.016/8314.3*Humidity/100*6.1078*pow(10,((7.5*Temperature)/(237.3+Temperature)))/(Temperature+273.15)";
        double t = 20, h = 50;
        double expected = Math.pow(10, 5) * 18.016 / 8314.3 * h / 100 * 6.1078 * Math.pow(10, (7.5 * t) / (237.3 + t)) / (t + 273.15);
        assertEquals(expected, eval(f, "Humidity", h, "Temperature", t), 1e-9);
        assertEquals(8.64, expected, 0.01); // g/m³ at 20 °C, 50 %
    }

    @Test
    public void roundAsMathRound() {
        assertEquals(3.0, eval("round(2.5)"), 0);
        assertEquals(-2.0, eval("round(-2.5)"), 0); // Math.round rounds half towards positive infinity
        assertEquals(2.34, eval("round(2.345, 2)"), 0); // two arguments as in EvalEx (half even)
        assertEquals(2.36, eval("round(2.355, 2)"), 0);
        assertEquals(2.35, eval("round(2.3451, 2)"), 0);
    }

    @Test
    public void otherJEvalFunctions() {
        assertEquals(3.0, eval("ceil(2.1)"), 0);
        assertEquals(Math.E, eval("exp(1)"), 1e-12);
        assertEquals(2.0, eval("rint(2.5)"), 0);
        assertEquals(180.0, eval("toDegrees(x)", "x", Math.PI), 1e-9);
        assertEquals(Math.PI, eval("toRadians(180)"), 1e-12);
        assertEquals(Math.IEEEremainder(10, 3), eval("IEEEremainder(10, 3)"), 0);
    }

    /** EvalEx' default precision (7 digits) rounded meter readings */
    @Test
    public void sixteenDigits() {
        assertEquals(478233.17 + 0.01, eval("a + b", "a", "478233.17", "b", "0.01"), 1e-9);
        assertEquals(12345678.123456, eval("a * 1", "a", "12345678.123456"), 1e-9);
    }

    /** input identifiers with spaces and umlauts, valid with JEval (demo calculation "Strom - Summe") */
    @Test
    public void identifiersWithSpacesAndUmlauts() {
        String formula = "#{Strom_Zähler A}+#{Strom_Zähler B}+#{Strom_Zähler C}";
        CalcExpressions.create(CalcExpressions.normalize(formula)).toRPN();
        CalcTemplate template = new CalcTemplate(1, formula);
        template.put("Strom_Zähler A", new BigDecimal("1.5"));
        template.put("Strom_Zähler B", new BigDecimal("2"));
        template.put("Strom_Zähler C", new BigDecimal("3.25"));
        assertEquals(6.75, template.evaluate().doubleValue(), 0);

        assertEquals("Strom_Gesamt", CalcExpressions.variable("Strom_Gesamt")); // simple names stay as they are
        assertEquals("a + b", CalcExpressions.normalize("#{a} + #{b}"));
        assertEquals("v_1_2d_x", CalcExpressions.variable("1-x"));
    }

    @Test
    public void syntaxCheckDoesNotEvaluate() {
        // valid, although it divides by zero with equal dummy values
        CalcExpressions.create("a/(b-c)").toRPN();
    }

    @Test(expected = Expression.ExpressionException.class)
    public void unknownFunctionIsStillAnError() {
        CalcExpressions.create("foo(1)").toRPN();
    }
}
