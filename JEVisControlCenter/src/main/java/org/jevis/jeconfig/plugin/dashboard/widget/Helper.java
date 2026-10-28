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
package org.jevis.jeconfig.plugin.dashboard.widget;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Helper {
    private static final Logger logger = LogManager.getLogger(Helper.class);

    public static  double convertToPercent(double value, double maximum,double minimum, int decimalPlaces) {
        BigDecimal bd;
        if (maximum > 0 && maximum>minimum) {
            try{

                bd = new BigDecimal((value-minimum) / (maximum-minimum) * 100).setScale(decimalPlaces, RoundingMode.HALF_DOWN);
                return bd.doubleValue();
            }
            catch (Exception e){
                logger.error("Failed to convert value to percent", e);
                return 0;
            }
        } else return 0;


    }

}
