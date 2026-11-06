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

import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.datetime.WorkDays;
import org.joda.time.DateTime;

/**
 * Computes the data of all rows of an analysis for a time frame, like the Control Center's {@code ChartPlugin.update()}
 * does when an analysis is shown: the time frame of the tool bar is resolved with the workday of the analysis
 * ({@link TimeFrameResolver}), aggregation, manipulation and the day filter of the analysis are applied to every row
 * (rows with an own interval keep it), and each row is loaded by {@link RowDataLoader}.
 */
public class AnalysisEvaluator {
    private final JEVisDataSource ds;
    private final RowDataLoader rowDataLoader;

    public AnalysisEvaluator(JEVisDataSource ds) {
        this.ds = ds;
        this.rowDataLoader = new RowDataLoader(ds);
    }

    /**
     * Resolves the time frame of the settings with the workday of the analysis (see {@link TimeFrameResolver}).
     *
     * @param analysisObject the analysis (its building defines the workday), may be {@code null}
     */
    public TimeRange resolveTimeFrame(JEVisObject analysisObject, EvaluationSettings settings) {
        WorkDays workDays = new WorkDays(analysisObject);
        DateTime now = settings.getNow() != null ? settings.getNow() : DateTime.now(settings.getZone());
        TimeFrame timeFrame = settings.getTimeFrame() != null ? settings.getTimeFrame() : AnalysisDefinition.DEFAULT_TIME_FRAME;
        return TimeFrameResolver.resolve(timeFrame, settings.getFrom(), settings.getTo(),
                workDays.getWorkdayStart(), workDays.getWorkdayEnd(), settings.getZone(), now);
    }

    /**
     * Evaluates all rows of the analysis.
     *
     * @param analysisObject the analysis object (for the workday), may be {@code null}
     * @param definition     the analysis read by {@link AnalysisReader}
     * @param settings       time frame, aggregation, manipulation, time zone, ...
     */
    public AnalysisResult evaluate(JEVisObject analysisObject, AnalysisDefinition definition, EvaluationSettings settings) {
        return evaluate(definition, resolveTimeFrame(analysisObject, settings), settings);
    }

    /**
     * Evaluates all rows of the analysis for an already resolved time frame.
     */
    public AnalysisResult evaluate(AnalysisDefinition definition, TimeRange range, EvaluationSettings settings) {
        AnalysisResult result = new AnalysisResult();
        result.setFrom(range.getStart());
        result.setTo(range.getEnd());
        DayFilter dayFilter = definition.createDayFilter();
        for (ChartDefinition chart : definition.getCharts()) {
            ChartResult chartResult = new ChartResult(chart.getId());
            for (RowDefinition row : chart.getRows()) {
                chartResult.getRows().add(rowDataLoader.load(row, chart.getType(), range, dayFilter, settings));
            }
            result.getCharts().add(chartResult);
        }
        return result;
    }

    /**
     * Evaluates a single row of the analysis (identified by its key) for an already resolved time frame.
     *
     * @return the result or {@code null} if the analysis has no row with this key
     */
    public RowResult evaluateRow(AnalysisDefinition definition, String key, TimeRange range, EvaluationSettings settings) {
        for (ChartDefinition chart : definition.getCharts()) {
            for (RowDefinition row : chart.getRows()) {
                if (key.equals(row.getKey())) {
                    return rowDataLoader.load(row, chart.getType(), range, definition.createDayFilter(), settings);
                }
            }
        }
        return null;
    }

    public JEVisDataSource getDataSource() {
        return ds;
    }
}
