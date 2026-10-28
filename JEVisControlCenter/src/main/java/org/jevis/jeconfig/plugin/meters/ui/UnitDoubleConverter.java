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
package org.jevis.jeconfig.plugin.meters.ui;

import javafx.util.converter.DoubleStringConverter;
import org.jevis.commons.i18n.I18n;

import java.text.NumberFormat;
import java.text.ParseException;

public class UnitDoubleConverter extends DoubleStringConverter {

    NumberFormat nf = NumberFormat.getNumberInstance(I18n.getInstance().getLocale());

    @Override
    public Double fromString(String value) {

        Double dvalue = null;
        try {
            dvalue = NumberFormat.getNumberInstance(I18n.getInstance().getLocale()).parse(value).doubleValue();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return dvalue;
    }

    @Override
    public String toString(Double value) {
        if (value == null) {
            return "";
        }
        nf.setMinimumFractionDigits(2);

        return nf.format(value);
    }


}
