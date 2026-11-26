/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
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
package org.jevis.commons.certificate;

import org.joda.time.LocalDate;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CertificateLogicTest {

    private static EnergyCertificate certificate(String validUntil) {
        EnergyCertificate c = new EnergyCertificate();
        c.setType(EnergyCertificate.Type.CONSUMPTION);
        c.setIssued("2017-03-01");
        c.setValidUntil(validUntil);
        return c;
    }

    @Test
    public void statusByDateWithTheLargestReminderPeriod() {
        LocalDate today = new LocalDate(2026, 11, 26);
        assertEquals(CertificateLogic.Status.EXPIRED, CertificateLogic.status(certificate("2026-11-25"), today));
        assertEquals(CertificateLogic.Status.EXPIRING, CertificateLogic.status(certificate("2026-11-26"), today));
        assertEquals(CertificateLogic.Status.EXPIRING, CertificateLogic.status(certificate("2027-05-26"), today));
        assertEquals(CertificateLogic.Status.VALID, CertificateLogic.status(certificate("2027-05-27"), today));
        assertEquals(CertificateLogic.Status.UNKNOWN, CertificateLogic.status(certificate(null), today));
    }

    @Test
    public void remindersSixAndOneMonthBeforeOnceEach() {
        EnergyCertificate c = certificate("2027-05-31");
        assertNull(CertificateLogic.dueReminder(c, new LocalDate(2026, 11, 29), Collections.emptyList()));
        assertEquals(Integer.valueOf(6), CertificateLogic.dueReminder(c, new LocalDate(2026, 11, 30), Collections.emptyList()));
        assertNull(CertificateLogic.dueReminder(c, new LocalDate(2027, 1, 15), Collections.singletonList(6)));
        assertEquals(Integer.valueOf(1), CertificateLogic.dueReminder(c, new LocalDate(2027, 4, 30), Collections.singletonList(6)));
        /* a missed 6-month reminder is covered by the 1-month reminder (only one mail) */
        assertEquals(Integer.valueOf(1), CertificateLogic.dueReminder(c, new LocalDate(2027, 5, 2), Collections.emptyList()));
        assertNull(CertificateLogic.dueReminder(c, new LocalDate(2027, 6, 1), Collections.emptyList()));
    }

    @Test
    public void validationReportsFieldsAndKeys() {
        EnergyCertificate c = new EnergyCertificate();
        c.setIssued("2017-03-01");
        c.setValidUntil("2016-01-01");
        c.setEnergyClass("Z");
        c.setFinalEnergy(-1d);
        c.setReminderMonths(Arrays.asList(6, 30));
        List<String[]> problems = CertificateLogic.validate(c);
        assertTrue(contains(problems, "type", "required"));
        assertTrue(contains(problems, "validUntil", "order"));
        assertTrue(contains(problems, "energyClass", "class"));
        assertTrue(contains(problems, "finalEnergy", "number"));
        assertTrue(contains(problems, "reminderMonths", "range"));

        EnergyCertificate ok = certificate("2027-02-28");
        ok.setEnergyClass("C");
        assertTrue(CertificateLogic.validate(ok).isEmpty());
    }

    @Test
    public void jsonRoundTripAndDefaultValidity() {
        EnergyCertificate c = certificate("2027-02-28");
        c.setRegistrationNumber("HE-2017-001234567");
        c.setResponsible(Arrays.asList(11L, 12L));
        c.setFinalEnergy(142.5);
        EnergyCertificate back = CertificateLogic.fromJson(CertificateLogic.toJson(c));
        assertEquals("HE-2017-001234567", back.getRegistrationNumber());
        assertEquals(Arrays.asList(11L, 12L), back.getResponsible());
        assertEquals(142.5, back.getFinalEnergy(), 1e-9);
        assertEquals(Arrays.asList(6, 1), back.getReminderMonths());
        assertNull(CertificateLogic.fromJson("not json"));
        assertEquals("2027-02-28", CertificateLogic.defaultValidUntil("2017-03-01"));
    }

    private static boolean contains(List<String[]> problems, String field, String key) {
        for (String[] p : problems) if (p[0].equals(field) && p[1].equals(key)) return true;
        return false;
    }
}
