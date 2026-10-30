/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.dialog;

import org.jevis.jeconfig.dialog.EnergyBalanceExpression.Sign;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class EnergyBalanceExpressionTest {

    @Test
    public void identifiers() {
        assertEquals("s1", EnergyBalanceExpression.subIdentifier(0));
        assertEquals("s12", EnergyBalanceExpression.subIdentifier(11));
        assertEquals(Arrays.asList("main", "s1", "s2", "s3"), EnergyBalanceExpression.identifiers(3));
        assertEquals(Collections.singletonList("main"), EnergyBalanceExpression.identifiers(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeIndex() {
        EnergyBalanceExpression.subIdentifier(-1);
    }

    @Test
    public void differenceWithSignedSubMeters() {
        assertEquals("#{main} - #{s1} - #{s2} + #{s3}",
                EnergyBalanceExpression.difference(Arrays.asList(Sign.MINUS, Sign.MINUS, Sign.PLUS)));
    }

    @Test
    public void differenceDefaultsToMinus() {
        assertEquals("#{main} - #{s1}", EnergyBalanceExpression.difference(Collections.singletonList(null)));
        assertEquals("#{main}", EnergyBalanceExpression.difference(Collections.emptyList()));
    }

    @Test
    public void percentage() {
        assertEquals("100 * (#{main} - #{s1} + #{s2}) / #{main}",
                EnergyBalanceExpression.percentage(Arrays.asList(Sign.MINUS, Sign.PLUS)));
    }

    @Test
    public void expressionEvaluates() {
        String normalized = EnergyBalanceExpression.percentage(Arrays.asList(Sign.MINUS, Sign.PLUS))
                .replaceAll("#\\{(\\w+)}", "$1");
        com.udojava.evalex.Expression expression = new com.udojava.evalex.Expression(normalized);
        expression.setVariable("main", new java.math.BigDecimal("200"));
        expression.setVariable("s1", new java.math.BigDecimal("150"));
        expression.setVariable("s2", new java.math.BigDecimal("10"));
        assertEquals(0, new java.math.BigDecimal("30").compareTo(expression.eval()));
    }
}
