/**
 * Copyright (C) JEVis contributors
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
package org.jevis.jeconfig.tool;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisSample;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal tool to generate simple test data for testing.
 */
public class TestDataGenerator {

    /**
     * Copy all double values to the same attribute but with an year offset
     *
     * @param jeVisAttribute
     * @param fromYear
     * @param toYear
     */
    public static void copyYearToNext(JEVisAttribute jeVisAttribute, int fromYear, int toYear) {
        try {
            List<JEVisSample> sampleList = jeVisAttribute.getSamples(
                    new DateTime(fromYear, 1, 1, 1, 1, 1),
                    new DateTime(fromYear, 12, 31, 1, 1, 1));
            List<JEVisSample> newSamples = new ArrayList<>();
            sampleList.forEach(jeVisSample -> {
                try {
                    newSamples.add(jeVisAttribute.buildSample(jeVisSample.getTimestamp().plusYears(toYear - fromYear), jeVisSample.getValueAsDouble()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            jeVisAttribute.addSamples(newSamples);

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
