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
package org.jevis.commons.dataprocessing.forecast;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisSample;
import org.jevis.commons.constants.GapFillingBoundToSpecific;
import org.jevis.commons.constants.GapFillingType;
import org.jevis.commons.datetime.DayClass;
import org.jevis.commons.regression.LinearTrendLine;
import org.jevis.commons.regression.TrendLine;
import org.joda.time.DateTime;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Forecast core shared by the data processor ({@code ForecastStep}) and the Control Center: selects the
 * reference samples for a timestamp and aggregates them (minimum, maximum, median, average or linear
 * regression).
 */
public final class ForecastCalculator {
    private static final Logger logger = LogManager.getLogger(ForecastCalculator.class);
    private static final double MILLIS_PER_DAY = 24d * 60d * 60d * 1000d;

    private ForecastCalculator() {
    }

    /**
     * Reference samples for {@code target}: same time of day and, depending on {@code bind}, the same
     * weekday class (holidays separate, see {@link DayClass}), week of year or month of year.
     * If the target is a holiday but the references contain no holiday, the plain weekday is used.
     */
    public static List<JEVisSample> selectReferenceSamples(List<JEVisSample> samples, DateTime target,
                                                           GapFillingBoundToSpecific bind, Predicate<LocalDate> holidays) {
        List<JEVisSample> result = new ArrayList<>();
        if (samples == null || samples.isEmpty() || target == null) return result;
        if (bind == null) bind = GapFillingBoundToSpecific.NONE;

        collect(samples, target, bind, holidays, result);
        if (result.isEmpty() && bind == GapFillingBoundToSpecific.WEEKDAY && DayClass.isHoliday(target, holidays)) {
            collect(samples, target, bind, null, result);
        }
        return result;
    }

    private static void collect(List<JEVisSample> samples, DateTime target, GapFillingBoundToSpecific bind,
                                Predicate<LocalDate> holidays, List<JEVisSample> result) {
        for (JEVisSample sample : samples) {
            DateTime ts;
            try {
                ts = sample.getTimestamp();
            } catch (Exception e) {
                continue;
            }
            if (ts == null || ts.getHourOfDay() != target.getHourOfDay() || ts.getMinuteOfHour() != target.getMinuteOfHour()) {
                continue;
            }
            switch (bind) {
                case WEEKDAY:
                    if (DayClass.sameWeekdayClass(ts, target, holidays)) result.add(sample);
                    break;
                case WEEKOFYEAR:
                    if (ts.getWeekOfWeekyear() == target.getWeekOfWeekyear()) result.add(sample);
                    break;
                case MONTHOFYEAR:
                    if (ts.getMonthOfYear() == target.getMonthOfYear()) result.add(sample);
                    break;
                default:
                    result.add(sample);
                    break;
            }
        }
    }

    /**
     * Aggregates the reference samples.
     *
     * @return the value or {@code null} if there is no usable reference value
     */
    public static Double calcValue(List<JEVisSample> references, GapFillingType type, DateTime target) {
        List<double[]> points = new ArrayList<>();
        if (references != null) {
            for (JEVisSample sample : references) {
                try {
                    Double value = sample.getValueAsDouble();
                    if (value != null && !value.isNaN() && !value.isInfinite()) {
                        points.add(new double[]{sample.getTimestamp().getMillis(), value});
                    }
                } catch (Exception e) {
                    logger.debug("Skipping sample", e);
                }
            }
        }
        if (points.isEmpty() || type == null) return null;

        switch (type) {
            case MINIMUM: {
                double min = Double.MAX_VALUE;
                for (double[] p : points) min = Math.min(min, p[1]);
                return min;
            }
            case MAXIMUM: {
                double max = -Double.MAX_VALUE;
                for (double[] p : points) max = Math.max(max, p[1]);
                return max;
            }
            case MEDIAN: {
                List<Double> sorted = new ArrayList<>();
                for (double[] p : points) sorted.add(p[1]);
                Collections.sort(sorted);
                if (sorted.size() > 2) return sorted.get(sorted.size() / 2);
                if (sorted.size() == 2) return (sorted.get(0) + sorted.get(1)) / 2;
                return sorted.get(0);
            }
            case AVERAGE:
                return average(points);
            case REGRESSION:
                return regression(points, target);
            default:
                return null;
        }
    }

    public static Double forecastValue(List<JEVisSample> samples, DateTime target, GapFillingType type,
                                       GapFillingBoundToSpecific bind, Predicate<LocalDate> holidays) {
        return calcValue(selectReferenceSamples(samples, target, bind, holidays), type, target);
    }

    private static double average(List<double[]> points) {
        double sum = 0d;
        for (double[] p : points) sum += p[1];
        return sum / points.size();
    }

    /**
     * Linear least squares fit of value over time (in days relative to the target), evaluated at the
     * target. Falls back to the average if there are fewer than three points or all points share one
     * timestamp.
     */
    private static double regression(List<double[]> points, DateTime target) {
        if (target == null || points.size() < 3) return average(points);
        double[] x = new double[points.size()];
        double[] y = new double[points.size()];
        boolean distinct = false;
        for (int i = 0; i < points.size(); i++) {
            x[i] = (points.get(i)[0] - target.getMillis()) / MILLIS_PER_DAY;
            y[i] = points.get(i)[1];
            if (x[i] != x[0]) distinct = true;
        }
        if (!distinct) return average(points);
        try {
            TrendLine trendLine = new LinearTrendLine();
            trendLine.setValues(y, x);
            double value = trendLine.predict(0d);
            return Double.isNaN(value) || Double.isInfinite(value) ? average(points) : value;
        } catch (Exception e) {
            logger.warn("Regression failed, using average: {}", e.getMessage());
            return average(points);
        }
    }
}
