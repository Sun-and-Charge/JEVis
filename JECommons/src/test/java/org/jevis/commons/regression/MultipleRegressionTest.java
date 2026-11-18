/**
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
package org.jevis.commons.regression;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class MultipleRegressionTest {

    private static SortedMap<Long, Double> series(double... values) {
        SortedMap<Long, Double> map = new TreeMap<>();
        for (int i = 0; i < values.length; i++) map.put((long) i + 1, values[i]);
        return map;
    }

    /** same textbook case as the web app test: y = 2.2 + 0.6 x, R² 0.6, t 2.121, p 0.124 */
    @Test
    public void simpleRegressionLikeAStatisticsPackage() {
        MultipleRegression r = MultipleRegression.fit(series(2, 4, 5, 4, 5), Collections.singletonList(series(1, 2, 3, 4, 5)));
        assertNull(r.getProblem());
        assertEquals(2.2, r.getCoefficients()[0], 1e-10);
        assertEquals(0.6, r.getCoefficients()[1], 1e-10);
        assertEquals(0.6, r.getRSquared(), 1e-10);
        assertEquals(0.4667, r.getAdjustedRSquared(), 1e-4);
        assertEquals(Math.sqrt(0.8), r.getRmse(), 1e-10);
        assertEquals(0.28284, r.getStandardErrors()[1], 1e-5);
        assertEquals(2.12132, r.getT()[1], 1e-5);
        assertEquals(0.124, r.getP()[1], 1e-3);
        assertEquals(2.8, r.getModelled()[0], 1e-10);
    }

    @Test
    public void twoDriversOverTheCommonTimestamps() {
        double[] a = {3, 1, 4, 1, 5, 9, 2, 6};
        double[] b = {2, 7, 1, 8, 2, 8, 1, 8};
        double[] y = new double[8];
        for (int i = 0; i < 8; i++) y[i] = 10 + 2 * a[i] - 3 * b[i];
        SortedMap<Long, Double> drop = series(b);
        drop.remove(4L);
        MultipleRegression r = MultipleRegression.fit(series(y), Arrays.asList(series(a), drop));
        assertEquals(7, r.getN());
        assertEquals(10, r.getCoefficients()[0], 1e-8);
        assertEquals(2, r.getCoefficients()[1], 1e-8);
        assertEquals(-3, r.getCoefficients()[2], 1e-8);
    }

    @Test
    public void problems() {
        assertEquals(MultipleRegression.Problem.NO_DRIVERS, MultipleRegression.fit(series(1, 2, 3), Collections.emptyList()).getProblem());
        assertEquals(MultipleRegression.Problem.TOO_FEW_POINTS, MultipleRegression.fit(series(1, 2), Collections.singletonList(series(1, 2))).getProblem());
        assertEquals(MultipleRegression.Problem.COLLINEAR, MultipleRegression.fit(series(1, 3, 2, 5),
                Arrays.asList(series(1, 2, 3, 4), series(2, 4, 6, 8))).getProblem());
    }
}
