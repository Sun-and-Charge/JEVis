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
package org.jevis.jeconfig.plugin.dashboard.config2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * JSON configuration of the alarm widget: an optional alarm configuration or alarm directory the alarms are
 * counted for (0 = all alarm configurations the user can read).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlarmCountNode {
    private Long alarmObject = 0L;

    public Long getAlarmObject() {
        return alarmObject;
    }

    public void setAlarmObject(Long alarmObject) {
        this.alarmObject = alarmObject;
    }
}
