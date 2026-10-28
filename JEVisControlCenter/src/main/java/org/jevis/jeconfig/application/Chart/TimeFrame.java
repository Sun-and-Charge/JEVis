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
package org.jevis.jeconfig.application.Chart;

import org.jevis.commons.datetime.DateHelper;
import org.jevis.commons.i18n.I18n;

public enum TimeFrame {
    CUSTOM, CURRENT, TODAY, YESTERDAY, LAST_7_DAYS, THIS_WEEK, LAST_WEEK, LAST_30_DAYS, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR, THE_YEAR_BEFORE_LAST, CUSTOM_START_END, PREVIEW;

    public static DateHelper.TransformType parseTransformType(TimeFrame timeFrame) {
        switch (timeFrame) {
            case CUSTOM:
                return DateHelper.TransformType.CUSTOM;
            case CURRENT:
                return DateHelper.TransformType.CURRENT;
            case TODAY:
                return DateHelper.TransformType.TODAY;
            case YESTERDAY:
                return DateHelper.TransformType.YESTERDAY;
            case LAST_7_DAYS:
                return DateHelper.TransformType.LAST7DAYS;
            case THIS_WEEK:
                return DateHelper.TransformType.THISWEEK;
            case LAST_WEEK:
                return DateHelper.TransformType.LASTWEEK;
            case LAST_30_DAYS:
                return DateHelper.TransformType.LAST30DAYS;
            case THIS_MONTH:
                return DateHelper.TransformType.THISMONTH;
            case LAST_MONTH:
                return DateHelper.TransformType.LASTMONTH;
            case THIS_YEAR:
                return DateHelper.TransformType.THISYEAR;
            case LAST_YEAR:
                return DateHelper.TransformType.LASTYEAR;
            case THE_YEAR_BEFORE_LAST:
                return DateHelper.TransformType.THEYEARBEFORELAST;
            case CUSTOM_START_END:
                return DateHelper.TransformType.CUSTOM_PERIOD;
            case PREVIEW:
                return DateHelper.TransformType.PREVIEW;
        }
        return null;
    }

    public static String getTranslationName(TimeFrame timeFrame) {
        String translatedName = "";
        switch (timeFrame) {
            case CUSTOM:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttoncustom");
                break;
            case CURRENT:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttoncurrent");
                break;
            case TODAY:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttontoday");
                break;
            case YESTERDAY:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttonyesterday");
                break;
            case LAST_7_DAYS:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttonlast7days");
                break;
            case THIS_WEEK:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttonthisweek");
                break;
            case LAST_WEEK:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttonlastweek");
                break;
            case LAST_30_DAYS:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttonlast30days");
                break;
            case THIS_MONTH:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttonthismonth");
                break;
            case LAST_MONTH:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttonlastmonth");
                break;
            case THIS_YEAR:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttonthisyear");
                break;
            case LAST_YEAR:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttonlastyear");
                break;
            case THE_YEAR_BEFORE_LAST:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttontheyearbeforelast");
                break;
            case CUSTOM_START_END:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.buttoncustomstartend");
                break;
            case PREVIEW:
                translatedName += I18n.getInstance().getString("plugin.graph.changedate.preview");
                break;
        }
        return translatedName;
    }
}
