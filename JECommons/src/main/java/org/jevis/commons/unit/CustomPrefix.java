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
package org.jevis.commons.unit;

import javax.measure.Prefix;
import javax.measure.Quantity;
import javax.measure.Unit;

public enum CustomPrefix implements Prefix {
    NONE("", 0);

    private final String symbol;
    private final int exponent;

    CustomPrefix(String symbol, int exponent) {
        this.symbol = symbol;
        this.exponent = exponent;
    }

    public static <Q extends Quantity<Q>> Unit<Q> NONE(Unit<Q> unit) {
        return unit.prefix(NONE);
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public Number getValue() {
        return 10;
    }

    @Override
    public int getExponent() {
        return exponent;
    }
}
