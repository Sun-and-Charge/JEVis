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
package org.jevis.commons.calculation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisSample;
import org.joda.time.DateTime;

import java.math.BigDecimal;

/**
 * Lightweight wrapper around a single time-series data point used internally by the
 * calculation engine.
 * <p>
 * {@code date} and {@code value} may be updated after construction to allow zero-fill for
 * ASYNC inputs. {@code variable} maps to the identifier used in the formula expression,
 * and {@code calcInputType} records the originating {@link CalcInputType} for merger logic.
 */
public class Sample {
    private static final Logger logger = LogManager.getLogger(Sample.class);

    private DateTime date;
    private BigDecimal value;
    private final String variable;
    private final CalcInputType calcInputType;

    Sample(JEVisSample currentSample, String variable, CalcInputType calcInputType) {
        try {
            this.date = currentSample.getTimestamp();
            this.value = new BigDecimal(currentSample.getValueAsDouble());
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
        this.variable = variable;
        this.calcInputType = calcInputType;
    }

    public DateTime getDate() {
        return date;
    }

    public BigDecimal getValue() {
        return value;
    }

    public String getVariable() {
        return variable;
    }

    public CalcInputType getCalcInputType() {
        return calcInputType;
    }
}
