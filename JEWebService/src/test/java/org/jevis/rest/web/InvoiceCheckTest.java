/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/** Invoice check R03 with a fake environment; neutral test data. */
public class InvoiceCheckTest {

    private static final DateTimeZone Z = DateTimeZone.forID("Europe/Berlin");
    private static final DateTime NOW = new DateTime(2026, 10, 6, 9, 0, Z);

    static final class Fake implements InvoiceCheck.Env {
        InvoiceCheck.Invoice invoice = new InvoiceCheck.Invoice();
        List<InvoiceCheck.Contract> contracts = new ArrayList<>(Collections.singletonList(
                new InvoiceCheck.Contract(30, "Strom Halle 3", "DE 0000 0000 0000 0000 0000 0000 0000 0001")));
        Map<String, Double> formulas = new HashMap<>();
        InvoiceCheck.Settings settings = new InvoiceCheck.Settings();
        String stored;
        int mails;
        DateTime from, to;
        boolean failCalculation;

        Fake() {
            invoice.id = 7;
            invoice.number = "R-1";
            invoice.meterPoint = "de00000000000000000000000000000001";
            invoice.periodStart = new DateTime(2026, 9, 1, 0, 0, Z);
            invoice.periodEnd = new DateTime(2026, 9, 30, 0, 0, Z);
            invoice.consumption = 4321.0;
            invoice.consumptionUnit = "kWh";
            invoice.net = 1460.96;
            formulas.put(InvoiceCheck.MEASURED, 4300.0);
            formulas.put(InvoiceCheck.EXPECTED, 1455.0);
        }

        @Override
        public InvoiceCheck.Invoice invoice(long id) {
            return invoice;
        }

        @Override
        public List<InvoiceCheck.Contract> contracts() {
            return contracts;
        }

        @Override
        public Map<String, Double> calculate(long configuration, DateTime from, DateTime to) {
            if (failCalculation) throw new IllegalStateException("template broken");
            this.from = from;
            this.to = to;
            return formulas;
        }

        @Override
        public InvoiceCheck.Settings settings(long invoiceId) {
            return settings;
        }

        @Override
        public void store(long invoiceId, String json, DateTime now) {
            stored = json;
        }

        @Override
        public boolean mail(InvoiceCheck.Settings settings, InvoiceCheck.Invoice invoice, Map<String, Object> result) {
            mails++;
            return true;
        }
    }

    private static JsonNode stored(Fake fake) throws Exception {
        return new ObjectMapper().readTree(fake.stored);
    }

    @Test
    public void withinTolerancesIsOk() throws Exception {
        Fake fake = new Fake();
        Map<String, Object> result = InvoiceCheck.check(fake, 7, NOW);
        /* 4321 vs 4300 = +0.49 % (tolerance 2 %), 1460.96 vs 1455 = +0.41 % (tolerance 1 %) */
        assertEquals(InvoiceCheck.OK, result.get("status"));
        JsonNode json = stored(fake);
        assertEquals("OK", json.get("status").asText());
        assertEquals(30, json.get("contract").get("id").asLong());
        assertEquals(0.49, json.get("quantity").get("deviationPercent").asDouble(), 1e-9);
        assertEquals("kWh", json.get("quantity").get("unit").asText());
        assertEquals(5.96, json.get("amount").get("difference").asDouble(), 1e-9);
        assertEquals(new DateTime(2026, 9, 1, 0, 0, Z), fake.from);
        assertEquals(new DateTime(2026, 9, 30, 23, 59, 59, 999, Z), fake.to);
        assertEquals(0, fake.mails);
    }

    @Test
    public void deviationIsMailedOnce() throws Exception {
        Fake fake = new Fake();
        fake.formulas.put(InvoiceCheck.EXPECTED, 1400.0);
        Map<String, Object> result = InvoiceCheck.check(fake, 7, NOW);
        assertEquals(InvoiceCheck.DEVIATION, result.get("status"));
        assertFalse(stored(fake).get("amount").get("ok").asBoolean());
        assertTrue(stored(fake).get("quantity").get("ok").asBoolean());
        assertEquals(1, fake.mails);
        /* checked again with the same deviation: no second mail */
        fake.invoice.previousStatus = InvoiceCheck.DEVIATION;
        InvoiceCheck.check(fake, 7, NOW.plusHours(1));
        assertEquals(1, fake.mails);
        /* own tolerance of the folder */
        fake.invoice.previousStatus = null;
        fake.settings.toleranceAmount = 5;
        assertEquals(InvoiceCheck.OK, InvoiceCheck.check(fake, 7, NOW).get("status"));
    }

    @Test
    public void notCheckableReasons() throws Exception {
        Fake fake = new Fake();
        fake.invoice.meterPoint = null;
        assertEquals("noMeterPoint", InvoiceCheck.check(fake, 7, NOW).get("reason"));
        fake.invoice.meterPoint = "DE999";
        assertEquals("noContract", InvoiceCheck.check(fake, 7, NOW).get("reason"));
        fake.invoice.meterPoint = "DE00000000000000000000000000000001";
        fake.invoice.periodEnd = null;
        assertEquals("noPeriod", InvoiceCheck.check(fake, 7, NOW).get("reason"));
        fake.invoice.periodEnd = new DateTime(2026, 9, 30, 0, 0, Z);
        fake.formulas.clear();
        assertEquals("noCheckFormulas", InvoiceCheck.check(fake, 7, NOW).get("reason"));
        fake.failCalculation = true;
        Map<String, Object> failed = InvoiceCheck.check(fake, 7, NOW);
        assertEquals(InvoiceCheck.NOT_CHECKABLE, failed.get("status"));
        assertEquals("calculation", failed.get("reason"));
        assertEquals("NOT_CHECKABLE", stored(fake).get("status").asText());
        assertEquals(0, fake.mails);
    }

    @Test
    public void partialCheckAndZeroMeasurement() {
        Fake fake = new Fake();
        fake.formulas.remove(InvoiceCheck.EXPECTED);
        Map<String, Object> result = InvoiceCheck.check(fake, 7, NOW);
        assertEquals(InvoiceCheck.OK, result.get("status"));
        assertEquals(true, result.get("partial"));
        assertNull(result.get("amount"));
        Map<String, Object> zero = InvoiceCheck.compare(0.0, 12.0, 2);
        assertNull(zero.get("deviationPercent"));
        assertEquals(false, zero.get("ok"));
        assertEquals(true, InvoiceCheck.compare(0.0, 0.0, 2).get("ok"));
        assertNull(InvoiceCheck.compare(null, 1.0, 2));
    }

    @Test
    public void severalIdentifiersInTheContract() {
        Fake fake = new Fake();
        fake.contracts.clear();
        fake.contracts.add(new InvoiceCheck.Contract(31, "Gas", "50000000001"));
        fake.contracts.add(new InvoiceCheck.Contract(32, "Strom", "10000000001; DE 0000 0000 0000 0000 0000 0000 0000 0001"));
        Map<String, Object> result = InvoiceCheck.check(fake, 7, NOW);
        assertEquals(32L, ((Map<?, ?>) result.get("contract")).get("id"));
        fake.invoice.meterPoint = "10000000001";
        assertEquals(32L, ((Map<?, ?>) InvoiceCheck.check(fake, 7, NOW).get("contract")).get("id"));
        assertFalse(InvoiceCheck.matches("1000000000", "10000000001"));
        assertFalse(InvoiceCheck.matches(";", ""));
    }

    @Test
    public void mailTextAndHelpers() {
        Fake fake = new Fake();
        fake.formulas.put(InvoiceCheck.EXPECTED, 1400.0);
        Map<String, Object> result = InvoiceCheck.check(fake, 7, NOW);
        String html = InvoiceCheckMail.html(fake.invoice, result);
        assertTrue(html, html.contains("R-1"));
        assertTrue(html, html.contains("Nettobetrag – Abweichung"));
        assertTrue(html, html.contains("1.460,96 €"));
        assertTrue(html, html.contains("Menge – in Ordnung"));
        assertEquals("OK", InvoiceCheck.status("{\"status\":\"OK\"}"));
        assertNull(InvoiceCheck.status("kaputt"));
        assertEquals(new DateTime(2026, 9, 1, 0, 0, Z), InvoiceCheck.date("2026-09-01T00:00:00.000+02:00", Z));
    }
}
