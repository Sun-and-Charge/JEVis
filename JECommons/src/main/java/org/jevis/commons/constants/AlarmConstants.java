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
package org.jevis.commons.constants;

public interface AlarmConstants {

    enum Operator {
        BIGGER, BIGGER_EQUALS, EQUALS, NOT_EQUALS, SMALLER, SMALLER_EQUALS;

        public static String getValue(Operator operator) {
            switch (operator) {
                case BIGGER:
                    return ">";
                case BIGGER_EQUALS:
                    return ">=";
                case EQUALS:
                    return "=";
                case NOT_EQUALS:
                    return "!=";
                case SMALLER:
                    return "<";
                case SMALLER_EQUALS:
                    return "<=";
                default:
                    return "";
            }
        }

        public static Operator parse(String operator) {
            switch (operator) {
                case "BIGGER":
                    return BIGGER;
                case "BIGGER_EQUALS":
                    return BIGGER_EQUALS;
                case "EQUALS":
                    return EQUALS;
                case "NOT_EQUALS":
                    return NOT_EQUALS;
                case "SMALLER":
                    return SMALLER;
                case "SMALLER_EQUALS":
                    return SMALLER_EQUALS;
                default:
                    return null;
            }
        }
    }
}
