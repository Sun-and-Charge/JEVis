/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEAlarm.
 * <p>
 * JEAlarm is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEAlarm is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEAlarm. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEAlarm is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jealarm.deprecated.limitalarm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

/**
 * @author fs
 */
public class AlarmPeriodOld {
    private static final Logger logger = LogManager.getLogger(AlarmPeriodOld.class);

    private DateTime alarmRaist;
    private final List<Double> sollValues = new ArrayList<>();
    private double tolerance = 0;
    private final List<Double> istValues = new ArrayList<>();
    private DateTime lastAlarm;

    public AlarmPeriodOld() {
    }

    public void addAlarmPoint(DateTime date, double istValue, double sollValue, int state) {
        sollValues.add(sollValue);
        istValues.add(istValue);
        if (alarmRaist == null) {
            alarmRaist = date;
        }
        lastAlarm = date;
        logger.info("---Add to period: {} new end: {}", alarmRaist, lastAlarm);
    }

    public void setTolerance(Double tol) {
        this.tolerance = tol;
    }

    public double getTolerance() {
        return tolerance;
    }

    public double getSumSoll() {
        double sum = 0;
        if (sollValues.size() > 0) {
            for (Double v : sollValues) {
                sum += v;
            }

            sum = sum / sollValues.size();
        }

        return sum;
    }

    public double getSumIst() {
        double sum = 0;
        if (istValues.size() > 0) {
            for (Double v : istValues) {
                sum += v;
            }

            sum = sum / istValues.size();
        }

        return sum;
    }

    public DateTime getPeriodEnd() {
        return lastAlarm;
    }

    public DateTime getPeriodStart() {
        return alarmRaist;
    }

}
