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
package org.jevis.commons.dataprocessing.processor.workflow;

/**
 * Discriminates which type of data-processing pipeline is active for a given
 * {@link ProcessManager} run.
 *
 * <p>Having a single enum field instead of three separate boolean flags makes
 * impossible states (e.g. {@code isClean=true} AND {@code isForecast=true})
 * unrepresentable at compile time.</p>
 */
public enum ProcessingType {

    /**
     * Standard clean-data pipeline: PrepareStep → alignment → gap fill → diff → scaling → ImportStep.
     */
    CLEAN,

    /**
     * Forecast pipeline: PrepareForecast → ForecastStep → ImportStep.
     */
    FORECAST,

    /**
     * Math/formula pipeline: PrepareMath → MathStep → ImportStep.
     */
    MATH
}
