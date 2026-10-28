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

import org.jevis.api.JEVisUnit;
import tech.units.indriya.unit.Units;

import javax.measure.MetricPrefix;
import javax.measure.Unit;


public interface CommonUnits {

    interface kWH {
        Unit unit = MetricPrefix.KILO(Units.WATT.multiply(Units.HOUR));
        JEVisUnit jevisUnit = new JEVisUnitImp(unit);
    }

    interface MWh {
        Unit unit = MetricPrefix.MEGA(Units.WATT.multiply(Units.HOUR));
        JEVisUnit jevisUnit = new JEVisUnitImp(unit);
    }

    interface kW {
        Unit unit = MetricPrefix.KILO(Units.WATT);
        JEVisUnit jevisUnit = new JEVisUnitImp(unit);
    }

    interface m3 {
        Unit unit = Units.CUBIC_METRE;
        JEVisUnit jevisUnit = new JEVisUnitImp(unit);
    }

    interface celsius {
        Unit unit = Units.CELSIUS;
        JEVisUnit jevisUnit = new JEVisUnitImp(unit);
    }
}
