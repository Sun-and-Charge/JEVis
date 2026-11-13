/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

public class DemoConfigTest {

    @Test
    public void targetIds() {
        assertEquals(Collections.singletonList(23048L), DemoConfig.parseIds("23048"));
        assertEquals(Arrays.asList(8596L, 23048L), DemoConfig.parseIds("8596;23048"));
        assertEquals(Arrays.asList(12L, 13L), DemoConfig.parseIds("12:Value; 13:Value;12"));
        assertTrue(DemoConfig.parseIds("").isEmpty());
        assertTrue(DemoConfig.parseIds(null).isEmpty());
        assertTrue(DemoConfig.parseIds("abc").isEmpty());
    }

    @Test
    public void demoNameCheck() {
        assertTrue(DemoConfig.isDemoName("Sun and Charge Demo"));
        assertTrue(DemoConfig.isDemoName("DEMO Werk"));
        assertFalse(DemoConfig.isDemoName("Musterwerk"));
        assertFalse(DemoConfig.isDemoName(null));
    }

    @Test
    public void shiftedUntilFormats() {
        assertEquals(LocalDate.of(2026, 11, 6), DemoConfig.parseDate("2026-11-06"));
        assertEquals(LocalDate.of(2026, 11, 6), DemoConfig.parseDate("2026-11-06T00:00:00.000+01:00"));
        // UTC midnight of the Berlin day before is still that Berlin day
        assertEquals(LocalDate.of(2026, 11, 6), DemoConfig.parseDate("2026-11-05T23:00:00.000Z"));
        assertNull(DemoConfig.parseDate(""));
        assertEquals("2026-11-13T00:00:00.000+01:00", DemoConfig.formatDate(LocalDate.of(2026, 11, 13)));
        assertEquals(LocalDate.of(2026, 11, 13), DemoConfig.parseDate(DemoConfig.formatDate(LocalDate.of(2026, 11, 13))));
    }

    @Test
    public void invoiceCases() {
        String json = "[{\"key\":\"A\",\"medium\":\"ELECTRICITY\",\"suffix\":\"SR\",\"directory\":24600,"
                + "\"consumption\":{\"row\":23734},\"peak\":{\"row\":\"23734\",\"factor\":4},"
                + "\"vat\":{\"object\":23519,\"attribute\":\"VAT Electricity\"},"
                + "\"lines\":[{\"key\":\"ENERGY\",\"text\":\"Arbeitspreis\",\"type\":\"ENERGY\",\"price\":{\"object\":23510,\"attribute\":\"Energy Price HT\"},\"priceUnit\":\"ct/kWh\"}],"
                + "\"deviations\":[{\"month\":\"2026-03\",\"type\":\"DUPLICATE_LINE\",\"line\":\"ENERGY\"},{\"month\":\"07\",\"type\":\"QUANTITY_FACTOR\",\"factor\":1.04}],"
                + "\"unknown\":true},"
                + "{\"key\":\"B\",\"medium\":\"GAS\",\"suffix\":\"GS\",\"directory\":\"@B.invoices\",\"consumption\":{\"row\":\"@B.clean\"},"
                + "\"lines\":[{\"key\":\"ENERGY\",\"type\":\"ENERGY\",\"price\":{\"object\":1,\"attribute\":\"Energy Price\"}}]},"
                + "{\"key\":\"bad\",\"directory\":{}}]";
        java.util.List<InvoiceCase> cases = DemoConfig.parseInvoiceCases(json);
        assertEquals("the unreadable entry is skipped", 2, cases.size());
        InvoiceCase a = cases.get(0);
        assertTrue(a.problems().toString(), a.problems().isEmpty());
        assertEquals(Ref.id(23734), a.peak.row);
        assertEquals(4, a.peak.factor, 0);
        assertEquals(1, a.deviations(java.time.YearMonth.of(2026, 3)).size());
        assertEquals("every July", 1, a.deviations(java.time.YearMonth.of(2027, 7)).size());
        assertTrue(a.deviations(java.time.YearMonth.of(2027, 3)).isEmpty());
        InvoiceCase b = cases.get(1);
        assertEquals(Ref.key("B.invoices"), b.directory);
        assertTrue(b.problems().toString(), b.problems().contains("directory is the unresolved key @B.invoices"));
        assertTrue(DemoConfig.parseInvoiceCases("").isEmpty());
        assertTrue(DemoConfig.parseInvoiceCases("{\"x\":1}").isEmpty());
        assertEquals(2, DemoConfig.parseInvoiceCases("{\"cases\":" + json + "}").size());
    }

    @Test
    public void replayStateRoundTrip() {
        Map<Long, String> state = new TreeMap<>();
        state.put(101L, "P13W");
        state.put(7L, "P5M");
        Map<Long, String> back = DemoConfig.parseState(DemoConfig.formatState(state));
        assertEquals(state, back);
        assertTrue(DemoConfig.parseState("{\"5\":\"bogus\"}").isEmpty());
        assertTrue(DemoConfig.parseState("not json").isEmpty());
    }
}
