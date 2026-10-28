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
package org.jevis.jeconfig.plugin.dashboard.timeframe;

import org.jevis.commons.i18n.I18n;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;

/**
 * Calculates an dynamic period
 */
public class LastPeriod implements TimeFrame {

    private final Period period;
    private final String listName;
    private final PeriodCode periodCode;

    public LastPeriod(PeriodCode periodCode) {
        this.periodCode = periodCode;

        switch (periodCode) {
            case PT24H:
                this.period = new Period("PT24H");
                this.listName = I18n.getInstance().getString("plugin.dashboard.timefactory.pt24h");
                break;
            case P7D:
                this.period = new Period("P7D");
                this.listName = I18n.getInstance().getString("plugin.dashboard.timefactory.p7d");
                break;
            case P30D:
                this.period = new Period("P30D");
                this.listName = I18n.getInstance().getString("plugin.dashboard.timefactory.p30d");
                break;
            case P365D:
                this.period = new Period("P365D");
                this.listName = I18n.getInstance().getString("plugin.dashboard.timefactory.p365d");
                break;
            case PALL:
                this.period = Period.ZERO;
                this.listName = I18n.getInstance().getString("plugin.dashboard.timefactory.pall");
                break;
            case LASTVALUE:
            default:
                this.period = Period.ZERO;
                this.listName = I18n.getInstance().getString("plugin.dashboard.timefactory.lastValue");
                break;
        }
    }

    public LastPeriod(String periodCode) {
        this(PeriodCode.valueOf(periodCode));
    }

    @Override
    public String getID() {
        return this.periodCode.toString();
    }

    @Override
    public String getListName() {
        return (this.listName.isEmpty())
                ? this.period.toString()
                : this.listName;
    }

    public PeriodCode getPeriodCode() {
        return periodCode;
    }

    @Override
    public Interval nextPeriod(Interval interval, int addAmount) {
//        Interval normalized = removeWorkdayInterval(interval);
//        return getInterval(normalized.getEnd().plus(Period.days(addAmount)));
        return null;
    }

    @Override
    public Interval previousPeriod(Interval interval, int addAmount) {
//        Interval normalized = removeWorkdayInterval(interval);
//        return getInterval(normalized.getStart().minus(Period.days(addAmount)));
        return null;
    }

    @Override
    public String format(Interval interval) {
        return DateTimeFormat.forPattern("yyyy-MM-dd HH:mm").print(interval.getStart()) + " / " + DateTimeFormat.forPattern("yyyy-MM-dd HH:mm").print(interval.getEnd());

    }

    @Override
    public Interval getInterval(DateTime dateTime, Boolean fixed) {
        if (dateTime.isAfterNow()) {
            dateTime = DateTime.now();
        }
        DateTime end = dateTime;
//        DateTime start = dateTime.withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0);
        DateTime start = dateTime.minus(this.period);

//        DateTime end = dateTime.withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59).withMillisOfSecond(999);
        return new Interval(start, end);
    }

    @Override
    public boolean hasNextPeriod(Interval interval) {
        return interval.getEnd().isAfterNow();
    }

    @Override
    public boolean hasPreviousPeriod(Interval interval) {
        return true;
    }

}
