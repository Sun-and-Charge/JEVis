/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.chart;

import de.jollyday.HolidayManager;
import de.jollyday.ManagerParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.datetime.HolidayCalendar;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;

/**
 * Day filter of an analysis (OpenJEVis issue #1448), port of the Control Center's
 * {@code org.jevis.jeconfig.application.Chart.data.DayFilter} with an explicit time zone.
 * <p>
 * Removes samples whose timestamp (in the user time zone) falls on an excluded weekday, a public holiday or an
 * explicitly excluded date. Only meaningful for data with a resolution of up to one day, see
 * {@link #isApplicable(AggregationPeriod, Period)}.
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

    /**
     * The filter works on the day of a sample's timestamp, so it is only applied to data with a resolution of up to
     * one day: aggregation NONE (with a raw period of at most one day), MINUTELY, QUARTER_HOURLY, HOURLY or DAILY.
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

    /**
     * Holiday check like the Control Center: public holidays of the user's locale (default holiday manager) or
     * the public/custom holidays of the building ({@link HolidayCalendar}).
     *
     * @param locale   locale for the default holidays (Control Center: the UI locale), may be {@code null}
     * @param building the building of the data row, may be {@code null}
     */
    public static Predicate<LocalDate> holidays(Locale locale, org.jevis.api.JEVisObject building) {
        HolidayManager defaultManager = null;
        if (locale != null) {
            try {
                defaultManager = HolidayManager.getInstance(ManagerParameters.create(locale));
            } catch (Exception e) {
                logger.debug("No default holiday manager for {}", locale, e);
            }
        }
        HolidayCalendar calendar = building != null ? HolidayCalendar.forBuilding(building) : null;
        final HolidayManager finalDefaultManager = defaultManager;
        return date -> {
            try {
                if (finalDefaultManager != null && finalDefaultManager.isHoliday(date)) return true;
            } catch (Exception e) {
                logger.warn("Could not check holiday for {}", date, e);
            }
            return calendar != null && calendar.isHoliday(date);
        };
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
     * @param timestamp the timestamp of a sample
     * @param zone      the time zone of the user
     * @param holidays  holiday check, only used if holidays are excluded; may be {@code null}
     * @return true if the sample is removed by this filter
     */
    public boolean isExcluded(DateTime timestamp, DateTimeZone zone, Predicate<LocalDate> holidays) {
        if (timestamp == null) return false;
        DateTime local = timestamp.withZone(zone);
        if (excludedWeekdays.contains(local.getDayOfWeek())) return true;

        LocalDate date = LocalDate.of(local.getYear(), local.getMonthOfYear(), local.getDayOfMonth());
        if (excludedDates.contains(date)) return true;

        return excludeHolidays && holidays != null && holidays.test(date);
    }

    /**
     * Returns a new list without the samples that fall on an excluded day. The input list is not modified.
     */
    public List<ChartSample> filter(List<ChartSample> samples, DateTimeZone zone, Predicate<LocalDate> holidays) {
        if (samples == null || !isActive()) return samples;
        Map<LocalDate, Boolean> cache = new HashMap<>();
        Predicate<LocalDate> cachedHolidays = holidays == null ? null : date -> cache.computeIfAbsent(date, holidays::test);
        List<ChartSample> result = new ArrayList<>(samples.size());
        for (ChartSample sample : samples) {
            try {
                if (!isExcluded(sample.getTimestamp(), zone, cachedHolidays)) {
                    result.add(sample);
                }
            } catch (Exception e) {
                result.add(sample);
            }
        }
        return result;
    }
}
