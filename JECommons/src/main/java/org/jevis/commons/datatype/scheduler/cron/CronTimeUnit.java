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

package org.jevis.commons.datatype.scheduler.cron;

public abstract class CronTimeUnit {
    public static final String NODATA = "";
    protected boolean isAlias = false;
    protected int denominator;

    public CronTimeUnit() {
    }

    public abstract String getValue();

    public boolean isAlias() {
        return this.isAlias;
    }

    public int getDenominator() {
        return this.denominator;
    }

    protected boolean isNumeratorValid(String num) {
        return num.equals("*");
    }

    protected boolean isDenominatorValid(String denom, int lim) {
        int den;
        try {
            den = Integer.parseInt(denom);
        } catch (NumberFormatException var5) {
            return false;
        }

        return den >= 0 && den <= lim;
    }

    protected boolean isValueValid(String t, int lim) {
        return this.isDenominatorValid(t, lim);
    }
}
