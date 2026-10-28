/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEReport.
 * <p>
 * JEReport is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEReport is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEReport. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEReport is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.report3.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.datetime.Period;
import org.jevis.report3.data.report.ReportConfiguration;
import org.joda.time.format.DateTimeFormat;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * @author broder
 */
public class DataHelper {
    private static final Logger logger = LogManager.getLogger(DataHelper.class);

    public static boolean checkAllObjectsNotNull(Object... objects) {
        boolean notNull = true;
        for (Object object : objects) {
            if (object == null) {
                notNull = false;
                break;
            }
        }
        return notNull;
    }

    public static boolean checkValidSchedule(String scheduleString) {
        try {
            Period.valueOf(scheduleString.toUpperCase());
        } catch (IllegalArgumentException ex) {
            logger.error("Schedule string is invalid: {}", scheduleString, ex);
            return false;
        }
        return true;
    }

    public static boolean checkValidDateFormat(String startRecordString) {
        try {
            DateTimeFormat.forPattern(ReportConfiguration.DATE_FORMAT).parseDateTime(startRecordString);
        } catch (IllegalArgumentException ex) {
            logger.error("Start record has non valid format: {}", startRecordString, ex);
            return false;
        }
        return true;
    }
}
