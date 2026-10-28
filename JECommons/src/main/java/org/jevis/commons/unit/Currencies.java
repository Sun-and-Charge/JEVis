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

import org.jevis.commons.unit.dimensions.Currency;
import tech.units.indriya.unit.BaseUnit;
import tech.units.indriya.unit.UnitDimension;

import javax.measure.Unit;

public class Currencies {

    public static final Unit<Currency> EUR = new BaseUnit<>("€", "Euro", UnitDimension.parse('C'));
    public static final Unit<Currency> GBP = new BaseUnit<>("£", "British Pound", UnitDimension.parse('C'));
    public static final Unit<Currency> USD = new BaseUnit<>("$", "US Dollar", UnitDimension.parse('C'));
    public static final Unit<Currency> AUD = new BaseUnit<>("$", "Australian Dollar", UnitDimension.parse('C'));
    public static final Unit<Currency> CAD = new BaseUnit<>("$", "Canadian Dollar", UnitDimension.parse('C'));
    public static final Unit<Currency> YEN = new BaseUnit<>("¥", "Yen", UnitDimension.parse('C'));
    public static final Unit<Currency> CNY = new BaseUnit<>("¥", "Renminbi Yuan", UnitDimension.parse('C'));
    public static final Unit<Currency> KRW = new BaseUnit<>("₩", "Won", UnitDimension.parse('C'));
    public static final Unit<Currency> TWD = new BaseUnit<>("元", "Taiwan Dollar", UnitDimension.parse('C'));
    public static final Unit<Currency> INR = new BaseUnit<>("₹", "Indian Rupee", UnitDimension.parse('C'));
}
