/**
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
package org.jevis.commons.object;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MeterQrPayloadTest {

    @Test
    public void payloadWithTargetAndServer() {
        assertEquals("jevis:meter?v=1&id=4711&target=5123:Value&server=https%3A%2F%2Fexample.org%3A443",
                MeterQrPayload.payload(4711, 5123L, "Value", "https://example.org:443/"));
    }

    @Test
    public void payloadEncodesAttributeNames() {
        assertEquals("jevis:meter?v=1&id=1&target=2:Raw%20Data", MeterQrPayload.payload(1, 2L, "Raw Data", null));
    }

    @Test
    public void payloadWithoutTargetAndServer() {
        assertEquals("jevis:meter?v=1&id=42", MeterQrPayload.payload(42, null, null, " "));
    }
}
