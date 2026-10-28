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
/*****************************************************************************
 *                                                                           *
 * BI Common - convert Number <-> String                                     *
 *                                                                           *
 * modified: 2017-04-25 Harald Braeuning                                     *
 *                                                                           *
 ****************************************************************************/

package org.jevis.jeconfig.application.Chart.ChartElements;

import com.ibm.icu.text.NumberFormat;
import de.gsi.chart.utils.NumberFormatter;
import javafx.util.StringConverter;
import org.jevis.commons.i18n.I18n;

/**
 * @author braeun
 */
public class CustomStringConverter extends StringConverter<Number> implements NumberFormatter {

    private final NumberFormat format = NumberFormat.getInstance(I18n.getInstance().getLocale());
    private int min = 6;
    private int max = 6;

    public CustomStringConverter() {
        this(6, 6);
    }

    public CustomStringConverter(int min, int max) {
        this.min = min;
        this.max = max;
        buildFormat();
    }

    private void buildFormat() {
        this.format.setMinimumFractionDigits(min);
        this.format.setMaximumFractionDigits(max);
    }


    @Override
    public Number fromString(String string) {
        return Double.parseDouble(string);
    }

    /*
     * (non-Javadoc)
     *
     * @see de.gsi.chart.utils.NumberFormatter#getPrecision()
     */
    @Override
    public int getPrecision() {
        return max;
    }

    @Override
    public boolean isExponentialForm() {
        return false;
    }

    @Override
    public NumberFormatter setExponentialForm(boolean state) {
        return this;
    }

    /*
     * (non-Javadoc)
     *
     * @see de.gsi.chart.utils.NumberFormatter#setPrecision(int)
     */
    @Override
    public NumberFormatter setPrecision(int precision) {
        this.min = precision;
        this.max = precision;
        buildFormat();
        return this;
    }

    public NumberFormatter setPrecision(int min, int max) {
        this.min = min;
        this.max = max;
        buildFormat();
        return this;
    }

    @Override
    public String toString(double val) {
        return toString(Double.valueOf(val));
    }

    @Override
    public String toString(Number object) {
        return format.format(object);
    }

}
