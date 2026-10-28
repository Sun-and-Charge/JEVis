/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEStatus.
 * <p>
 * JEStatus is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEStatus is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEStatus. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEStatus is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jestatus;

import com.fasterxml.jackson.databind.JsonNode;

public class SimCardInfos {
    private JsonNode simUsage;
    private JsonNode simDetails;

    public SimCardInfos(JsonNode simUsage, JsonNode simDetails) {
        this.simUsage = simUsage;
        this.simDetails = simDetails;
    }

    public JsonNode getSimUsage() {
        return simUsage;
    }

    public void setSimUsage(JsonNode simUsage) {
        this.simUsage = simUsage;
    }

    public JsonNode getSimDetails() {
        return simDetails;
    }

    public void setSimDetails(JsonNode simDetails) {
        this.simDetails = simDetails;
    }
}
