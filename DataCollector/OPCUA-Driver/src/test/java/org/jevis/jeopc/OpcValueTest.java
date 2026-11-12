/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of OPCUA-Driver.
 * <p>
 * OPCUA-Driver is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * OPCUA-Driver is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * OPCUA-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * OPCUA-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeopc;

import org.jevis.api.JEVisSample;
import org.jevis.commons.driver.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpcValueTest {

    @Test
    void jsonValuesBecomeJavaValues() {
        assertNull(OpcValue.toJava(null));
        assertNull(OpcValue.toJava(FakeTransport.json("null")));
        assertEquals(42L, OpcValue.toJava(FakeTransport.json("42")));
        assertEquals(4.5, OpcValue.toJava(FakeTransport.json("4.5")));
        assertEquals(Boolean.TRUE, OpcValue.toJava(FakeTransport.json("true")));
        assertEquals("text", OpcValue.toJava(FakeTransport.json("'text'")));
        assertEquals(Arrays.<Object>asList(1L, 2.5, "a"), OpcValue.toJava(FakeTransport.json("[1, 2.5, 'a']")));
        assertEquals(new java.math.BigInteger("18446744073709551615"), OpcValue.toJava(FakeTransport.json("18446744073709551615")));
    }

    @Test
    void readResultIsMapped() {
        OpcValue value = OpcValue.fromReadJson(FakeTransport.json("{'nodeId':'ns=2;s=Device1.Temp','value':21.5,'dataType':'Double',"
                + "'sourceTimestamp':'2026-11-12T08:15:00.000Z','serverTimestamp':'2026-11-12T09:15:00.120+01:00','status':'Good','statusCode':0}"));
        assertEquals("ns=2;s=Device1.Temp", value.getNodeId());
        assertEquals(21.5, value.getValue());
        assertEquals("Double", value.getDataType());
        assertEquals(new DateTime(2026, 11, 12, 8, 15, DateTimeZone.UTC).getMillis(), value.getTimestamp().getMillis());
        assertEquals(new DateTime(2026, 11, 12, 8, 15, 0, 120, DateTimeZone.UTC).getMillis(), value.getServerTimestamp().getMillis());
        assertTrue(value.isGood());
    }

    @Test
    void statusSeverity() {
        OpcValue uncertain = OpcValue.fromHistoryJson("n", FakeTransport.json("{'ts':'2026-11-12T08:15:00Z','value':1,'status':'UncertainLastUsableValue','statusCode':1083768832}"));
        assertTrue(uncertain.isUncertain());
        assertFalse(uncertain.isGood());
        assertFalse(uncertain.isBad());

        OpcValue goodNoData = OpcValue.fromHistoryJson("n", FakeTransport.json("{'ts':'2026-11-12T08:15:00Z','value':null,'status':'GoodNoData','statusCode':10878976}"));
        assertTrue(goodNoData.isGood());

        /* without numeric code the name decides */
        assertTrue(OpcValue.fromHistoryJson("n", FakeTransport.json("{'ts':null,'status':'BadNoData'}")).isBad());
        assertTrue(OpcValue.fromHistoryJson("n", FakeTransport.json("{'ts':null,'status':'UncertainInitialValue'}")).isUncertain());
        assertTrue(OpcValue.fromHistoryJson("n", FakeTransport.json("{'ts':null}")).isGood());
        assertNull(OpcValue.fromHistoryJson("n", FakeTransport.json("{'ts':null}")).getTimestamp());
    }

    @Test
    void sampleValueConversion() {
        assertEquals(3.0, value(3L).toSampleValue());
        assertEquals(2.5, value(2.5).toSampleValue());
        assertEquals(7.25, value("7.25").toSampleValue());
        assertEquals("on", value("on").toSampleValue());
        assertEquals("true", value(Boolean.TRUE).toSampleValue());
        assertNull(value(null).toSampleValue());
    }

    @Test
    void historyValuesBecomeSamplesAndStatusSamples() throws Exception {
        DateTimeZone berlin = DateTimeZone.forID("Europe/Berlin");
        DateTime ts = new DateTime(2026, 11, 1, 0, 0, DateTimeZone.UTC);
        List<OpcValue> values = Arrays.asList(
                new OpcValue("n", ts, null, 1.5, null, "Good", 0),
                new OpcValue("n", ts.plusMinutes(15), null, 4L, null, "Good", 0),
                new OpcValue("n", ts.plusMinutes(30), null, null, null, "BadNoData", 0x809B0000L),
                new OpcValue("n", ts.plusMinutes(45), null, 2.0, null, "UncertainLastUsableValue", 0x40900000L),
                new OpcValue("n", ts.plusMinutes(60), null, null, null, "GoodNoData", 0x00A60000L),
                new OpcValue("n", null, null, 9.0, null, "Good", 0));
        List<Result> results = new ArrayList<>();
        List<JEVisSample> status = new ArrayList<>();

        OPCUAServer.convertValues(values, "123:Value", berlin, results, status);

        assertEquals(2, results.size());
        assertEquals(1.5, results.get(0).getValue());
        assertEquals(4.0, results.get(1).getValue());
        assertEquals("123:Value", results.get(0).getTargetStr());
        assertEquals(ts.getMillis(), results.get(0).getDate().getMillis());
        assertEquals(berlin, results.get(0).getDate().getZone());

        assertEquals(2, status.size());
        assertEquals(0x809B0000L, (long) status.get(0).getValueAsLong());
        assertEquals(-1L, (long) status.get(1).getValueAsLong());
    }

    private static OpcValue value(Object value) {
        return new OpcValue("n", null, null, value, null, "Good", 0);
    }
}
