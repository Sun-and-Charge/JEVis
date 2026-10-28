/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of Notifier.
 * <p>
 * Notifier is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * Notifier is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * Notifier. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * Notifier is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jenotifier.exporter;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.commons.object.plugin.TargetHelper;
import org.joda.time.DateTime;

public class ExportDataEvent extends ExportEvent {

    public static String TYPE_Operator = "Optional";
    public static String TYPE_LIMIT = "Limit";
    public static String TYPE_JEVISID = "JEVis ID";


    protected String operator = "";
    /**
     * limit value is not an number because we also what to compare Strings
     **/
    protected String limitValue = "";
    protected JEVisAttribute sourceAttribute = null;

    public ExportDataEvent(JEVisObject object) {
        super(object);
        super.init();
    }

    @Override
    void initAttributes() {
        try {
            JEVisAttribute attOperator = eventObject.getAttribute(TYPE_Operator);
            operator = attOperator.getLatestSample().getValueAsString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            JEVisAttribute attLimit = eventObject.getAttribute(TYPE_LIMIT);
            limitValue = attLimit.getLatestSample().getValueAsString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            JEVisAttribute attJEvisID = eventObject.getAttribute(TYPE_JEVISID);
            TargetHelper targetHelper = new TargetHelper(eventObject.getDataSource(), attJEvisID);
            sourceAttribute = targetHelper.getAttribute().get(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    boolean isTriggered(DateTime lastUpdate) {
        if (sourceAttribute == null || sourceAttribute.getLatestSample() == null) return false;

        try {
            DateTime lastDataTS = sourceAttribute.getLatestSample().getTimestamp();
            if (operator.equals("New Data")) {
                return lastDataTS.isAfter(lastUpdate);
            }

            /** TODO: implement other operatoras like >,<, !=.... **/
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return false;
    }
}
