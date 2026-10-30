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

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the calculation expressions of an energy balance (OpenJEVis #2368): one main meter
 * minus (or plus) a number of sub-meters. Pure string logic, used by {@link EnergyBalanceWizard}.
 */
public final class EnergyBalanceExpression {

    /**
     * Identifier of the main meter input.
     */
    public static final String MAIN_IDENTIFIER = "main";
    /**
     * Prefix of the sub-meter input identifiers: s1, s2, ...
     */
    public static final String SUB_IDENTIFIER_PREFIX = "s";

    private EnergyBalanceExpression() {
    }

    /**
     * Sign of a sub-meter in the balance.
     */
    public enum Sign {
        /**
         * Subtracted from the main meter (normal sub-meter).
         */
        MINUS("-", "-"),
        /**
         * Added to the main side, e.g. a feed-in.
         */
        PLUS("+", "+");

        private final String label;
        private final String operator;

        Sign(String label, String operator) {
            this.label = label;
            this.operator = operator;
        }

        public String getOperator() {
            return operator;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * @param index zero based index of the sub-meter
     * @return identifier of the sub-meter input, "s1" for index 0
     */
    public static String subIdentifier(int index) {
        if (index < 0) throw new IllegalArgumentException("index < 0");
        return SUB_IDENTIFIER_PREFIX + (index + 1);
    }

    /**
     * @param subMeterCount number of sub-meters
     * @return all input identifiers, main first
     */
    public static List<String> identifiers(int subMeterCount) {
        List<String> identifiers = new ArrayList<>();
        identifiers.add(MAIN_IDENTIFIER);
        for (int i = 0; i < subMeterCount; i++) {
            identifiers.add(subIdentifier(i));
        }
        return identifiers;
    }

    private static String variable(String identifier) {
        return "#{" + identifier + "}";
    }

    /**
     * @param signs sign of each sub-meter, in order
     * @return difference expression, e.g. {@code #{main} - #{s1} - #{s2} + #{s3}}
     */
    public static String difference(List<Sign> signs) {
        StringBuilder sb = new StringBuilder(variable(MAIN_IDENTIFIER));
        for (int i = 0; i < signs.size(); i++) {
            Sign sign = signs.get(i) != null ? signs.get(i) : Sign.MINUS;
            sb.append(' ').append(sign.getOperator()).append(' ').append(variable(subIdentifier(i)));
        }
        return sb.toString();
    }

    /**
     * Difference in percent of the main meter. A division by zero (main = 0) is not an error,
     * the calculation replaces such a result with its static value.
     *
     * @param signs sign of each sub-meter, in order
     * @return e.g. {@code 100 * (#{main} - #{s1}) / #{main}}
     */
    public static String percentage(List<Sign> signs) {
        return "100 * (" + difference(signs) + ") / " + variable(MAIN_IDENTIFIER);
    }
}
