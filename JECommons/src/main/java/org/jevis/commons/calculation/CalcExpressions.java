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

import com.udojava.evalex.AbstractFunction;
import com.udojava.evalex.Expression;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Creates the EvalEx expressions of calculations, math data and formulas.
 * <ul>
 * <li>Precision: {@link MathContext#DECIMAL64} (16 digits, like {@code double}). EvalEx defaults to DECIMAL32,
 * 7 digits, which rounds e.g. a meter reading of 478233.17 to 478233.2.</li>
 * <li>Functions of the former parser JEval (java.lang.Math), used by formulas written before the switch to EvalEx:
 * {@code pow(a, b)}, {@code round(x)} (like Math.round; {@code round(x, n)} keeps the EvalEx meaning),
 * {@code ceil}, {@code exp}, {@code rint}, {@code toDegrees}, {@code toRadians}, {@code IEEEremainder}.</li>
 * </ul>
 */
public final class CalcExpressions {

    public static final MathContext MATH_CONTEXT = MathContext.DECIMAL64;

    private CalcExpressions() {
    }

    /**
     * @param formula the formula with plain variable names (no {@code #{...}})
     */
    public static Expression create(String formula) {
        Expression expression = new Expression(formula, MATH_CONTEXT);
        expression.addFunction(new AbstractFunction("POW", 2) {
            @Override
            public BigDecimal eval(List<BigDecimal> p) {
                return number(Math.pow(p.get(0).doubleValue(), p.get(1).doubleValue()));
            }
        });
        expression.addFunction(new AbstractFunction("ROUND", -1) {
            @Override
            public BigDecimal eval(List<BigDecimal> p) {
                if (p.size() == 1) {
                    // java.lang.Math.round: floor(x + 0.5), i.e. half towards positive infinity
                    return p.get(0).add(new BigDecimal("0.5")).setScale(0, RoundingMode.FLOOR);
                }
                if (p.size() == 2) {
                    return p.get(0).setScale(p.get(1).intValue(), MATH_CONTEXT.getRoundingMode());
                }
                throw new Expression.ExpressionException("ROUND expects 1 or 2 parameters");
            }
        });
        expression.addFunction(new AbstractFunction("CEIL", 1) {
            @Override
            public BigDecimal eval(List<BigDecimal> p) {
                return p.get(0).setScale(0, RoundingMode.CEILING);
            }
        });
        expression.addFunction(new AbstractFunction("EXP", 1) {
            @Override
            public BigDecimal eval(List<BigDecimal> p) {
                return number(Math.exp(p.get(0).doubleValue()));
            }
        });
        expression.addFunction(new AbstractFunction("RINT", 1) {
            @Override
            public BigDecimal eval(List<BigDecimal> p) {
                return p.get(0).setScale(0, RoundingMode.HALF_EVEN);
            }
        });
        expression.addFunction(new AbstractFunction("TODEGREES", 1) {
            @Override
            public BigDecimal eval(List<BigDecimal> p) {
                return number(Math.toDegrees(p.get(0).doubleValue()));
            }
        });
        expression.addFunction(new AbstractFunction("TORADIANS", 1) {
            @Override
            public BigDecimal eval(List<BigDecimal> p) {
                return number(Math.toRadians(p.get(0).doubleValue()));
            }
        });
        expression.addFunction(new AbstractFunction("IEEEREMAINDER", 2) {
            @Override
            public BigDecimal eval(List<BigDecimal> p) {
                return number(Math.IEEEremainder(p.get(0).doubleValue(), p.get(1).doubleValue()));
            }
        });
        return expression;
    }

    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("#\\{([^}]*)}");

    /**
     * Replaces every {@code #{name}} of a JEVis formula by the EvalEx variable {@link #variable(String) variable(name)}.
     * Names with spaces or other characters ({@code #{Strom_Zähler A}}) were valid with JEval and stay valid.
     */
    public static String normalize(String formula) {
        java.util.regex.Matcher m = PLACEHOLDER.matcher(formula);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(variable(m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * EvalEx variable name for an input identifier: unchanged if it is already a valid name (letters, digits,
     * underscore, not starting with a digit), otherwise every other character becomes {@code _<hex>_}.
     */
    public static String variable(String identifier) {
        if (identifier.matches("[\\p{L}_][\\p{L}\\p{N}_]*")) return identifier;
        StringBuilder sb = new StringBuilder();
        if (identifier.isEmpty() || !(Character.isLetter(identifier.charAt(0)) || identifier.charAt(0) == '_')) {
            sb.append("v_");
        }
        for (char c : identifier.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_') sb.append(c);
            else sb.append('_').append(Integer.toHexString(c)).append('_');
        }
        return sb.toString();
    }

    private static BigDecimal number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ArithmeticException("Result is not a finite number: " + value);
        }
        return new BigDecimal(value, MATH_CONTEXT);
    }
}
