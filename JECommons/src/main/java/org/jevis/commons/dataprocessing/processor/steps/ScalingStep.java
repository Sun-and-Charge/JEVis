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
package org.jevis.commons.dataprocessing.processor.steps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisSample;
import org.jevis.commons.dataprocessing.CleanDataObject;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.dataprocessing.processor.workflow.CleanInterval;
import org.jevis.commons.dataprocessing.processor.workflow.ProcessStep;
import org.jevis.commons.dataprocessing.processor.workflow.ResourceManager;
import org.jevis.commons.i18n.I18n;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author broder
 */
public class ScalingStep implements ProcessStep {

    private static final Logger logger = LogManager.getLogger(ScalingStep.class);

    public static BigDecimal getCurrentMultiplier(List<JEVisSample> listMultipliers, DateTime date) {
        BigDecimal multi = new BigDecimal(1);
        try {
            for (JEVisSample multiplier : listMultipliers) {
                int index = listMultipliers.indexOf(multiplier);
                DateTime timeStampOfMultiplier = null;
                DateTime nextTimeStampOfMultiplier = null;
                Double multiplierDouble = null;

                timeStampOfMultiplier = multiplier.getTimestamp();
                multiplierDouble = multiplier.getValueAsDouble();

                if (index + 1 < listMultipliers.size()) {
                    nextTimeStampOfMultiplier = listMultipliers.get(index + 1).getTimestamp();
                }
                if (date.equals(timeStampOfMultiplier) || date.isAfter(timeStampOfMultiplier) && ((nextTimeStampOfMultiplier == null) || date.isBefore(nextTimeStampOfMultiplier))) {
                    multi = new BigDecimal(multiplierDouble.toString());
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Could not get multiplier for {}", date, e);
        }
        return multi;
    }

    @Override
    public void run(ResourceManager resourceManager) throws Exception {
        DateTime benchStart = new DateTime();
        CleanDataObject cleanDataObject = resourceManager.getCleanDataObject();
        List<CleanInterval> intervals = resourceManager.getIntervals();
        List<JEVisSample> listMultipliers = cleanDataObject.getMultiplier();
        BigDecimal offset = new BigDecimal(cleanDataObject.getOffset().toString());

        for (CleanInterval currentInt : intervals) {
            BigDecimal currentMulti = getCurrentMultiplier(listMultipliers, currentInt.getDate());

            VirtualSample sample = currentInt.getResult();
            Double rawValue = sample.getValueAsDouble();

            if (rawValue != null) {
                BigDecimal rawValueDec = new BigDecimal(rawValue.toString());
                BigDecimal productDec = new BigDecimal(0);
                productDec = productDec.add(rawValueDec);
                productDec = productDec.multiply(currentMulti);
                productDec = productDec.add(offset);
                sample.setValue(productDec.doubleValue());
                String note = sample.getNote();
                note += ",scale(" + currentMulti + ")";
                sample.setNote(note);
            }
        }

        logger.debug("{} finished in {}", this.getClass().getSimpleName(), new Period(benchStart, new DateTime()).toString(PeriodFormat.wordBased(I18n.getInstance().getLocale())));
    }

}
