/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.trc;

import org.jevis.commons.chart.TimeRange;
import org.jevis.commons.dashboard.DashboardTimeFrames;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.*;

/**
 * The time frames of the result calculation (the Control Center's {@code IntervalSelector} with
 * {@code TimeFrameFactory.getReduced()}), headless, on top of {@link DashboardTimeFrames}. Ids as stored in
 * {@code intervalSelectorConfiguration} and in the time restrictions of inputs and formulas. Names are not
 * translated here, see {@link #nameKeys(String)}.
 */
public final class TrcTimeFrames {
    /**
     * {@code TimeFrameFactory.getReduced()}, in this order
     */
    public static final List<String> REDUCED = Collections.unmodifiableList(Arrays.asList(
            DashboardTimeFrames.DAY, DashboardTimeFrames.WEEK, DashboardTimeFrames.MONTH, DashboardTimeFrames.YEAR,
            DashboardTimeFrames.THREE_YEARS, DashboardTimeFrames.FIVE_YEARS, DashboardTimeFrames.TEN_YEARS,
            DashboardTimeFrames.CUSTOM));
    /**
     * "No time frame" of the restriction dialogs ({@code TimeFrameFactory.NONE})
     */
    public static final String NONE = "NONE";

    private final DashboardTimeFrames frames;

    /**
     * @param zone time zone of the user, {@code null} for the default
     * @param now  the current instant, {@code null} for the system clock
     */
    public TrcTimeFrames(DateTimeZone zone, DateTime now) {
        this.frames = new DashboardTimeFrames(null, zone, null, now);
    }

    /**
     * @return the time frames enabled in the template ({@code intervalSelectorConfiguration}), in the order of
     * {@link #REDUCED}
     */
    public static List<String> enabled(RCTemplate template) {
        List<String> ids = new ArrayList<>();
        Map<String, Boolean> configuration = template != null ? template.getIntervalSelectorConfiguration() : null;
        if (configuration == null) return ids;
        for (String id : REDUCED) {
            if (Boolean.TRUE.equals(configuration.get(id))) ids.add(id);
        }
        return ids;
    }

    /**
     * @return the keys of the JEVis language files whose texts, joined by a space, are the name of the time frame
     * (as {@code TimeFrame.getListName()}); empty for unknown ids
     */
    public static List<String> nameKeys(String id) {
        if (id == null) return Collections.emptyList();
        switch (id) {
            case DashboardTimeFrames.DAY:
                return Collections.singletonList("plugin.graph.interval.daily");
            case DashboardTimeFrames.WEEK:
                return Collections.singletonList("plugin.graph.interval.weekly");
            case DashboardTimeFrames.MONTH:
                return Collections.singletonList("plugin.graph.interval.monthly");
            case DashboardTimeFrames.YEAR:
                return Collections.singletonList("plugin.graph.interval.yearly");
            case DashboardTimeFrames.THREE_YEARS:
                return Arrays.asList("plugin.object.report.dialog.period.last", "plugin.object.report.dialog.aggregation.threeyears");
            case DashboardTimeFrames.FIVE_YEARS:
                return Arrays.asList("plugin.object.report.dialog.period.last", "plugin.object.report.dialog.aggregation.fiveyears");
            case DashboardTimeFrames.TEN_YEARS:
                return Arrays.asList("plugin.object.report.dialog.period.last", "plugin.object.report.dialog.aggregation.tenyears");
            case DashboardTimeFrames.CUSTOM:
                return Collections.singletonList("plugin.graph.changedate.buttoncustom");
            default:
                return Collections.emptyList();
        }
    }

    /**
     * {@code TimeFrame.getInterval(date, false)} of the reduced time frames: the period containing the date (dates in
     * the future are replaced by now); {@link DashboardTimeFrames#CUSTOM} is the day of the date (as the Control
     * Center's {@code custom()}).
     *
     * @return the interval or {@code null} for unknown ids and {@link #NONE}
     */
    public TimeRange interval(String id, DateTime date) {
        if (id == null || NONE.equals(id)) return null;
        if (DashboardTimeFrames.CUSTOM.equals(id)) {
            return frames.interval(DashboardTimeFrames.DAY, date, false);
        }
        return frames.interval(id, date, false);
    }

    public DateTimeZone getZone() {
        return frames.getZone();
    }

    public DateTime getNow() {
        return frames.getNow();
    }
}
