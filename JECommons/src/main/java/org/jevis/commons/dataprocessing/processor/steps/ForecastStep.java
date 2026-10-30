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
package org.jevis.commons.dataprocessing.processor.steps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisSample;
import org.jevis.commons.constants.GapFillingBoundToSpecific;
import org.jevis.commons.constants.GapFillingType;
import org.jevis.commons.constants.NoteConstants;
import org.jevis.commons.dataprocessing.ForecastDataObject;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.dataprocessing.forecast.ForecastCalculator;
import org.jevis.commons.dataprocessing.processor.workflow.CleanInterval;
import org.jevis.commons.dataprocessing.processor.workflow.ProcessStep;
import org.jevis.commons.dataprocessing.processor.workflow.ResourceManager;
import org.jevis.commons.datetime.HolidayCalendar;
import org.jevis.commons.json.JsonGapFillingConfig;

import java.util.List;
import java.util.Objects;

import static org.jevis.commons.constants.GapFillingType.parse;
import static org.jevis.commons.dataprocessing.processor.GapsAndLimits.getNote;

public class ForecastStep implements ProcessStep {
    private static final Logger logger = LogManager.getLogger(ForecastStep.class);

    static String getNoteSuffix(GapFillingType type) {
        switch (type) {
            case MINIMUM:
                return NoteConstants.Forecast.FORECAST_MIN;
            case MAXIMUM:
                return NoteConstants.Forecast.FORECAST_MAX;
            case MEDIAN:
                return NoteConstants.Forecast.FORECAST_MEDIAN;
            case AVERAGE:
                return NoteConstants.Forecast.FORECAST_AVERAGE;
            case REGRESSION:
                return NoteConstants.Forecast.FORECAST_REGRESSION;
            default:
                return null;
        }
    }

    @Override
    public void run(ResourceManager resourceManager) throws Exception {

        ForecastDataObject forecastDataObject = resourceManager.getForecastDataObject();
        List<CleanInterval> intervals = resourceManager.getIntervals();
        List<JEVisSample> sampleCache = forecastDataObject.getSampleCache();

        if (forecastDataObject.getTypeAttribute().hasSample()) {
            String type = forecastDataObject.getTypeAttribute().getLatestSample().getValueAsString();
            GapFillingType gapFillingType = parse(type);
            String noteSuffix = getNoteSuffix(gapFillingType);
            if (noteSuffix == null) {
                return;
            }

            JsonGapFillingConfig c = forecastDataObject.getJsonGapFillingConfig();
            GapFillingBoundToSpecific bind = GapFillingBoundToSpecific.parse(c.getBindtospecific());
            if (Objects.isNull(bind)) bind = GapFillingBoundToSpecific.NONE;

            HolidayCalendar holidays = null;
            if (bind == GapFillingBoundToSpecific.WEEKDAY) {
                holidays = HolidayCalendar.forObject(forecastDataObject.getForecastDataObject());
                if (holidays != null) {
                    logger.debug("Using building holidays as separate day class for forecast {}", forecastDataObject.getName());
                }
            }

            int lastIndex = intervals.size() - 1;
            for (int index = 0; index < intervals.size(); index++) {
                CleanInterval currentInterval = intervals.get(index);
                Double value = ForecastCalculator.forecastValue(sampleCache, currentInterval.getDate(), gapFillingType, bind, holidays);
                if (value == null) value = 0d;

                VirtualSample sample = currentInterval.getResult();
                sample.setTimeStamp(currentInterval.getDate());
                sample.setValue(value);
                String note = "";
                note += getNote(currentInterval);

                if (index == 0) {
                    note += "," + NoteConstants.Forecast.FORECAST_1 + noteSuffix;
                } else if (index == lastIndex) {
                    note += "," + NoteConstants.Forecast.FORECAST_2 + noteSuffix;
                } else {
                    note += "," + NoteConstants.Forecast.FORECAST + noteSuffix;
                }

                sample.setNote(note);
            }
        }

    }
}
