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
package org.jevis.commons.dataprocessing.processor.preparation;

import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.CleanDataObject;
import org.jevis.commons.dataprocessing.MathDataObject;
import org.jevis.commons.dataprocessing.processor.workflow.CleanInterval;
import org.jevis.commons.dataprocessing.processor.workflow.PeriodRule;
import org.jevis.commons.dataprocessing.processor.workflow.ProcessStep;
import org.jevis.commons.dataprocessing.processor.workflow.ResourceManager;
import org.jevis.commons.datetime.PeriodHelper;
import org.jevis.commons.datetime.WorkDays;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalTime;
import org.joda.time.Period;

import java.util.ArrayList;
import java.util.List;

public class PrepareMath implements ProcessStep {
    @Override
    public void run(ResourceManager resourceManager) throws Exception {
        MathDataObject mathDataObject = resourceManager.getMathDataObject();

        List<PeriodRule> inputPeriodRules = mathDataObject.getInputDataPeriodAlignment();
        List<PeriodRule> outputPeriodRules = mathDataObject.getPeriodAlignment();
        List<CleanInterval> intervals = new ArrayList<>();

        DateTime start = mathDataObject.getStartDate();
        DateTime end = mathDataObject.getEndDate();
        Period inputPeriod = CleanDataObject.getPeriodForDate(inputPeriodRules, start);
        Period outputPeriod = CleanDataObject.getPeriodForDate(outputPeriodRules, start);

        AggregationPeriod aggregationPeriod = mathDataObject.getReferencePeriod();
        WorkDays workDays = new WorkDays(mathDataObject.getMathDataObject());

        if ((aggregationPeriod.isGreaterThenDays() && workDays.isCustomWorkDay()) || (aggregationPeriod == AggregationPeriod.CUSTOM && PeriodHelper.isGreaterThenDays(outputPeriod))) {
            java.time.LocalTime wdStart = workDays.getWorkdayStart();
            java.time.LocalTime wdEnd = workDays.getWorkdayEnd();
            LocalTime dtStart = new LocalTime(wdStart.getHour(), wdStart.getMinute(), wdStart.getSecond(), 0);
            LocalTime dtEnd = new LocalTime(wdEnd.getHour(), wdEnd.getMinute(), wdEnd.getSecond(), 999);

            start = start.withTime(dtStart);
            end = end.withTime(dtEnd).plusMillis(1);

            if (dtEnd.isBefore(dtStart)) {
                start = start.minusDays(1);
            }
        }

        Long referencePeriodCount = mathDataObject.getReferencePeriodCount();
        Long periodOffset = mathDataObject.getPeriodOffset();

        Period relevantPeriod;
        List<PeriodRule> relevantRules;
        if (aggregationPeriod == AggregationPeriod.CUSTOM) {
            relevantPeriod = outputPeriod;
            relevantRules = outputPeriodRules;
        } else {
            relevantPeriod = inputPeriod;
            relevantRules = inputPeriodRules;
        }

        boolean periodHasYear = relevantPeriod.getYears() > 0;
        boolean periodHasMonths = relevantPeriod.getMonths() > 0;
        boolean periodHasWeeks = relevantPeriod.getWeeks() > 0;
        boolean periodHasDays = relevantPeriod.getDays() > 0;
        boolean periodHasHours = relevantPeriod.getHours() > 0;
        boolean periodHasMinutes = relevantPeriod.getMinutes() > 0;
        boolean periodHasSeconds = relevantPeriod.getSeconds() > 0;
        boolean periodHasMillis = relevantPeriod.getMillis() > 0;


        if (periodHasMonths || periodHasYear || periodHasWeeks || periodHasDays || periodHasHours || periodHasMinutes || periodHasSeconds || periodHasMillis) {
            if (mathDataObject.isFillPeriod()) {
                if (start != null && end != null) {
                    while (start.isBefore(end)) {
                        relevantPeriod = CleanDataObject.getPeriodForDate(relevantRules, start);
                        DateTime endOfInterval;
                        if (periodHasYear) {
                            endOfInterval = start.plusYears(relevantPeriod.getYears());
                            endOfInterval = endOfInterval.plusMonths(relevantPeriod.getMonths());
                            endOfInterval = addLesserPeriods(endOfInterval, relevantPeriod);

                            if (relevantPeriod.getMonths() == 0 && relevantPeriod.getYears() == 1 && relevantPeriod.getWeeks() == 0
                                    && relevantPeriod.getDays() == 0 && relevantPeriod.getHours() == 0 && relevantPeriod.getMinutes() == 0
                                    && relevantPeriod.getMillis() == 0) {
                                endOfInterval = endOfInterval.withMonthOfYear(endOfInterval.monthOfYear().getMaximumValue());
                                endOfInterval = endOfInterval.withDayOfMonth(endOfInterval.dayOfMonth().getMaximumValue());
                            }
                        } else if (periodHasMonths) {
                            endOfInterval = start.plusMonths(relevantPeriod.getMonths());
                            endOfInterval = endOfInterval.plusYears(relevantPeriod.getYears());
                            endOfInterval = addLesserPeriods(endOfInterval, relevantPeriod);

                            if (relevantPeriod.getMonths() == 1 && relevantPeriod.getYears() == 0 && relevantPeriod.getWeeks() == 0
                                    && relevantPeriod.getDays() == 0 && relevantPeriod.getHours() == 0 && relevantPeriod.getMinutes() == 0
                                    && relevantPeriod.getMillis() == 0) {
                                endOfInterval = endOfInterval.withDayOfMonth(endOfInterval.dayOfMonth().getMaximumValue());
                            }
                        } else {
                            endOfInterval = start.plus(relevantPeriod);
                        }
                        endOfInterval = endOfInterval.minusMillis(1);
                        Interval interval = new Interval(start, endOfInterval);
                        // the result of each filled period is moved by the period offset, as without period filling
                        DateTime resultDate = start;
                        if (periodOffset != 0L) {
                            resultDate = mathDataObject.getNextRunWithOffset(aggregationPeriod, periodOffset, referencePeriodCount, start);
                        }
                        CleanInterval cleanInterval = new CleanInterval(interval, resultDate);
                        intervals.add(cleanInterval);

                        if (periodHasYear) {
                            start = start.plusYears(relevantPeriod.getYears());
                            start = start.plusMonths(relevantPeriod.getMonths());
                            start = addLesserPeriods(start, relevantPeriod);

                            if (relevantPeriod.getMonths() == 0 && relevantPeriod.getYears() == 1 && relevantPeriod.getWeeks() == 0
                                    && relevantPeriod.getDays() == 0 && relevantPeriod.getHours() == 0 && relevantPeriod.getMinutes() == 0
                                    && relevantPeriod.getMillis() == 0) {
                                start = start.withMonthOfYear(start.monthOfYear().getMaximumValue());
                                start = start.withDayOfMonth(start.dayOfMonth().getMaximumValue());
                            }
                        } else if (periodHasMonths) {
                            start = start.plusMonths(relevantPeriod.getMonths());
                            start = start.plusYears(relevantPeriod.getYears());
                            start = addLesserPeriods(start, relevantPeriod);

                            if (relevantPeriod.getMonths() == 1 && relevantPeriod.getYears() == 0 && relevantPeriod.getWeeks() == 0
                                    && relevantPeriod.getDays() == 0 && relevantPeriod.getHours() == 0 && relevantPeriod.getMinutes() == 0
                                    && relevantPeriod.getMillis() == 0) {
                                start = start.withDayOfMonth(start.dayOfMonth().getMaximumValue());
                            }
                        } else {
                            start = start.plus(relevantPeriod);
                        }
                    }
                    resourceManager.setIntervals(intervals);
                }
            } else {
                Interval interval = new Interval(start, end.minusMillis(1));

                if (periodOffset != 0L) {
                    start = mathDataObject.getNextRunWithOffset(aggregationPeriod, periodOffset, referencePeriodCount, start);
                }

                CleanInterval cleanInterval = new CleanInterval(interval, start);
                intervals.add(cleanInterval);
                resourceManager.setIntervals(intervals);
            }
        } else {
            Interval interval = new Interval(start, end.minusMillis(1));

            if (periodOffset != 0L) {
                start = mathDataObject.getNextRunWithOffset(aggregationPeriod, periodOffset, referencePeriodCount, start);
            }

            CleanInterval cleanInterval = new CleanInterval(interval, start);
            intervals.add(cleanInterval);
            resourceManager.setIntervals(intervals);
        }
    }

    private DateTime addLesserPeriods(DateTime start, Period inputPeriod) {
        DateTime date = start;
        date = date.plusWeeks(inputPeriod.getWeeks());
        date = date.plusDays(inputPeriod.getDays());
        date = date.plusHours(inputPeriod.getHours());
        date = date.plusMinutes(inputPeriod.getMinutes());
        date = date.plusSeconds(inputPeriod.getSeconds());
        date = date.plusMillis(inputPeriod.getMillis());
        return date;
    }
}
