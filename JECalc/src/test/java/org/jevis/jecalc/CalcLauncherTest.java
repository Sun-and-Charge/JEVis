/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JECalc.
 * <p>
 * JECalc is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECalc is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECalc. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECalc is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jecalc;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * Smoke tests for CalcLauncher.
 */
public class CalcLauncherTest {

    @Test
    public void testClassLoads() {
        assertNotNull(CalcLauncher.class);
    }
}
