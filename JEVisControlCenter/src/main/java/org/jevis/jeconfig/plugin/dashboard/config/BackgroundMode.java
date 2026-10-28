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
package org.jevis.jeconfig.plugin.dashboard.config;

/**
 * @deprecated Legacy V1 dashboard background mode constants. This interface exists solely for
 * backward-compatibility when reading old dashboard JSON files.
 * Use {@link org.jevis.jeconfig.plugin.dashboard.config2.DashboardPojo} and the
 * {@code config2} package instead.
 */
@Deprecated
public interface BackgroundMode {

    String defaultMode="default";
    String repeat ="repeat";
    String stretch= "stretch";
}
