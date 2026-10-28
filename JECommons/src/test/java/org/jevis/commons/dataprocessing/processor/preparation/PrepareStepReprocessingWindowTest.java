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

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the reprocessing-window widening logic added to {@link PrepareStep#getIntervals} for
 * Fix 1 (bounded reprocessing window): the single "step back one period" is replaced by
 * "step back N periods", where N is {@code CleanDataObject.getReprocessingPeriods()}.
 *
 * <p>Mirrors the extracted-logic testing style of {@code PrepareStepDeduplicationTest} - full
 * {@link PrepareStep} construction requires a live JEVis data source, so the three branches of
 * the window computation are replicated here as plain Joda-Time arithmetic and asserted against
 * the exact formulas used in {@code PrepareStep.getIntervals()}.</p>
 */
public class PrepareStepReprocessingWindowTest {

    /**
     * Replicates the three branches added around the "step back" logic in
     * {@code PrepareStep.getIntervals()}.
     */
    private Window applyReprocessingWindow(DateTime currentDate, DateTime maxEndDate, Period lastPeriod,
                                           int reprocessingPeriods, boolean overnightWorkday, boolean firstIsDifferential) {
        Window w = new Window();
        w.currentDate = currentDate;
        w.maxEndDate = maxEndDate;

        Period lookbackPeriod = lastPeriod.multipliedBy(reprocessingPeriods);
        boolean weekMonthYearScale = lastPeriod.getYears() > 0 || lastPeriod.getMonths() > 0 || lastPeriod.getWeeks() > 0;
        boolean monthYearScale = lastPeriod.getYears() > 0 || lastPeriod.getMonths() > 0;

        if (overnightWorkday && weekMonthYearScale) {
            w.currentDate = currentDate.minus(lookbackPeriod);
            w.maxEndDate = maxEndDate.minus(lookbackPeriod);
        } else if (monthYearScale && firstIsDifferential) {
            w.maxEndDate = maxEndDate.minus(lookbackPeriod);
        } else if (reprocessingPeriods > 1) {
            w.currentDate = currentDate.minus(lookbackPeriod.minus(lastPeriod));
        }
        return w;
    }

    @Test
    public void defaultReprocessingPeriodsLeavesGeneralCaseUnchanged() {
        DateTime currentDate = new DateTime(2024, 3, 1, 0, 0);
        DateTime maxEndDate = new DateTime(2024, 4, 1, 0, 0);

        Window w = applyReprocessingWindow(currentDate, maxEndDate, Period.months(1), 1, false, false);

        assertEquals("N=1 must not move currentDate in the general case", currentDate, w.currentDate);
        assertEquals("N=1 must not move maxEndDate in the general case", maxEndDate, w.maxEndDate);
    }

    @Test
    public void generalCaseStepsBackNMinusOneAdditionalPeriods() {
        DateTime currentDate = new DateTime(2024, 3, 1, 0, 0);
        DateTime maxEndDate = new DateTime(2024, 4, 1, 0, 0);

        Window w = applyReprocessingWindow(currentDate, maxEndDate, Period.months(1), 3, false, false);

        assertEquals(currentDate.minusMonths(2), w.currentDate);
        assertEquals(maxEndDate, w.maxEndDate);
    }

    @Test
    public void overnightWorkdayBranchScalesBothDatesByN() {
        DateTime currentDate = new DateTime(2024, 3, 1, 0, 0);
        DateTime maxEndDate = new DateTime(2024, 4, 1, 0, 0);

        Window w = applyReprocessingWindow(currentDate, maxEndDate, Period.months(1), 3, true, false);

        assertEquals(currentDate.minusMonths(3), w.currentDate);
        assertEquals(maxEndDate.minusMonths(3), w.maxEndDate);
    }

    @Test
    public void overnightWorkdayBranchDefaultReproducesOriginalSinglePeriodStepBack() {
        DateTime currentDate = new DateTime(2024, 3, 1, 0, 0);
        DateTime maxEndDate = new DateTime(2024, 4, 1, 0, 0);

        Window w = applyReprocessingWindow(currentDate, maxEndDate, Period.months(1), 1, true, false);

        assertEquals(currentDate.minusMonths(1), w.currentDate);
        assertEquals(maxEndDate.minusMonths(1), w.maxEndDate);
    }

    @Test
    public void differentialMonthYearBranchScalesMaxEndDateByN() {
        DateTime currentDate = new DateTime(2024, 3, 1, 0, 0);
        DateTime maxEndDate = new DateTime(2024, 4, 1, 0, 0);

        Window w = applyReprocessingWindow(currentDate, maxEndDate, Period.months(1), 3, false, true);

        assertEquals(currentDate, w.currentDate);
        assertEquals(maxEndDate.minusMonths(3), w.maxEndDate);
    }

    private static final class Window {
        DateTime currentDate;
        DateTime maxEndDate;
    }
}
