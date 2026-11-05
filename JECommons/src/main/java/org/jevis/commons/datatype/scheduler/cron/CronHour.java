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

package org.jevis.commons.datatype.scheduler.cron;

import java.security.InvalidParameterException;

public class CronHour extends CronTimeUnit {
    private static final int LIMIT = 23;
    private String hour;

    public CronHour() {
    }

    public String getValue() {
        return this.hour;
    }

    protected void setValue(String str) throws InvalidParameterException {
        String tmp = str.replaceAll(" ", "");
        if (tmp.equalsIgnoreCase("")) {
            tmp = "*";
            this.isAlias = true;
            this.denominator = 0;
        } else if (tmp.equals("*")) {
            this.isAlias = true;
            this.denominator = 0;
        } else if (tmp.contains("/")) {
            String[] splStr = tmp.split("/");
            if (splStr.length == 2 && this.isNumeratorValid(splStr[0]) && this.isDenominatorValid(splStr[1], 23)) {
                this.isAlias = true;
                this.denominator = Integer.parseInt(splStr[1]);
            }
        } else if (!this.isValueValid(tmp, 23)) {
            throw new InvalidParameterException("time parameter " + tmp + " is wrong");
        }

        this.hour = tmp;
    }
}
