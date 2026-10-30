/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.jeconfig.application.Chart.data;

import de.jollyday.HolidayManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.jeconfig.application.tools.Holidays;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable day filter of an analysis (OpenJEVis issue #1448).
 * <p>
 * Removes samples whose timestamp (in the user time zone) falls on an excluded weekday,
 * a public holiday (see {@link Holidays}) or an explicitly excluded date. The filter is only
 * meaningful for data with a resolution of up to one day, see {@link #isApplicable(AggregationPeriod, Period)}.
 * <p>
 * The persistent form is stored in {@link DataModel} as plain strings:
 * weekdays as ISO numbers ({@code "6,7"} = Saturday and Sunday) and dates as ISO dates
 * separated by commas ({@code "2026-12-24,2026-12-31"}).
 */
public class DayFilter {
    private static final Logger logger = LogManager.getLogger(DayFilter.class);

    private final Set<Integer> excludedWeekdays;
    private final boolean excludeHolidays;
    private final Set<LocalDate> excludedDates;

    public DayFilter(Set<Integer> excludedWeekdays, boolean excludeHolidays, Set<LocalDate> excludedDates) {
        this.excludedWeekdays = Collections.unmodifiableSet(new TreeSet<>(excludedWeekdays));
        this.excludeHolidays = excludeHolidays;
        this.excludedDates = Collections.unmodifiableSet(new TreeSet<>(excludedDates));
    }

    public static DayFilter of(String weekdays, boolean excludeHolidays, String dates) {
        return new DayFilter(parseWeekdays(weekdays), excludeHolidays, parseDates(dates));
    }

    /**
     * @param weekdays comma separated ISO weekday numbers (1 = Monday ... 7 = Sunday)
     */
    public static Set<Integer> parseWeekdays(String weekdays) {
        Set<Integer> result = new TreeSet<>();
        if (weekdays == null) return result;
        for (String part : weekdays.split(",")) {
            try {
                int day = Integer.parseInt(part.trim());
                if (day >= 1 && day <= 7) {
                    result.add(day);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static String formatWeekdays(Collection<Integer> weekdays) {
        return new TreeSet<>(weekdays).stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * @param dates comma, semicolon or whitespace separated ISO dates (yyyy-MM-dd)
     */
    public static Set<LocalDate> parseDates(String dates) {
        Set<LocalDate> result = new TreeSet<>();
        if (dates == null) return result;
        for (String part : dates.split("[,;\\s]+")) {
            if (part.trim().isEmpty()) continue;
            try {
                result.add(LocalDate.parse(part.trim()));
            } catch (DateTimeParseException e) {
                logger.warn("Ignoring invalid day filter date '{}'", part);
            }
        }
        return result;
    }

    public static String formatDates(Collection<LocalDate> dates) {
        return new TreeSet<>(dates).stream().map(LocalDate::toString).collect(Collectors.joining(","));
    }

    /**
     * The filter works on the day of a sample's timestamp, so it is only applied to data
     * with a resolution of up to one day: aggregation NONE (with a raw period of at most one day),
     * MINUTELY, QUARTER_HOURLY, HOURLY or DAILY.
     *
     * @param aggregationPeriod the aggregation of the data row
     * @param rawPeriod         the raw period of the data row, may be {@code null}
     */
    public static boolean isApplicable(AggregationPeriod aggregationPeriod, Period rawPeriod) {
        if (aggregationPeriod == null) aggregationPeriod = AggregationPeriod.NONE;
        switch (aggregationPeriod) {
            case NONE:
                return rawPeriod == null
                        || (rawPeriod.getYears() == 0 && rawPeriod.getMonths() == 0 && rawPeriod.getWeeks() == 0 && rawPeriod.getDays() <= 1);
            case MINUTELY:
            case QUARTER_HOURLY:
            case HOURLY:
            case DAILY:
                return true;
            default:
                return false;
        }
    }

    public boolean isActive() {
        return !excludedWeekdays.isEmpty() || excludeHolidays || !excludedDates.isEmpty();
    }

    public Set<Integer> getExcludedWeekdays() {
        return excludedWeekdays;
    }

    public boolean isExcludeHolidays() {
        return excludeHolidays;
    }

    public Set<LocalDate> getExcludedDates() {
        return excludedDates;
    }

    /**
     * Returns a new list without the samples that fall on an excluded day. The input list is not modified.
     *
     * @param samples the samples to filter
     * @param site    the building of the data row, used for site specific holidays; may be {@code null}
     */
    public List<JEVisSample> filter(List<JEVisSample> samples, JEVisObject site) {
        if (samples == null || !isActive()) return samples;
        Map<LocalDate, Boolean> holidayCache = new HashMap<>();
        List<JEVisSample> result = new ArrayList<>(samples.size());
        for (JEVisSample sample : samples) {
            try {
                if (!isExcluded(sample.getTimestamp(), site, holidayCache)) {
                    result.add(sample);
                }
            } catch (Exception e) {
                result.add(sample);
            }
        }
        return result;
    }

    private boolean isExcluded(DateTime timestamp, JEVisObject site, Map<LocalDate, Boolean> holidayCache) {
        if (timestamp == null) return false;
        DateTime local = timestamp.withZone(DateTimeZone.getDefault());
        if (excludedWeekdays.contains(local.getDayOfWeek())) return true;

        LocalDate date = LocalDate.of(local.getYear(), local.getMonthOfYear(), local.getDayOfMonth());
        if (excludedDates.contains(date)) return true;

        if (excludeHolidays) {
            return holidayCache.computeIfAbsent(date, d -> isHoliday(d, site));
        }
        return false;
    }

    private static boolean isHoliday(LocalDate date, JEVisObject site) {
        try {
            HolidayManager defaultManager = Holidays.getDefaultHolidayManager();
            if (defaultManager != null && defaultManager.isHoliday(date)) return true;
            if (site != null) {
                HolidayManager siteManager = Holidays.getSiteHolidayManager(site);
                if (siteManager != null && siteManager.isHoliday(date, Holidays.getStateCode())) return true;
                HolidayManager customManager = Holidays.getCustomHolidayManager(site);
                if (customManager != null && customManager.isHoliday(date)) return true;
            }
        } catch (Exception e) {
            logger.warn("Could not check holiday for {}", date, e);
        }
        return false;
    }
}
