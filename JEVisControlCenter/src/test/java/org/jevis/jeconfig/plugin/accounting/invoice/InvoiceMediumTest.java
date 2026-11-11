/**
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
package org.jevis.jeconfig.plugin.accounting.invoice;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InvoiceMediumTest {

    @Test
    public void knownCodes() {
        assertEquals(InvoiceMedium.ELECTRICITY, InvoiceMedium.fromCode("ELECTRICITY"));
        assertEquals(InvoiceMedium.GAS, InvoiceMedium.fromCode("GAS"));
        assertEquals(InvoiceMedium.DISTRICT_HEATING, InvoiceMedium.fromCode("DISTRICT_HEATING"));
        assertEquals(InvoiceMedium.OTHER, InvoiceMedium.fromCode("OTHER"));
    }

    @Test
    public void lenientCodes() {
        assertEquals(InvoiceMedium.GAS, InvoiceMedium.fromCode(" gas "));
        assertEquals(InvoiceMedium.DISTRICT_HEATING, InvoiceMedium.fromCode("district-heating"));
    }

    @Test
    public void unknownCodesAreOther() {
        assertEquals(InvoiceMedium.OTHER, InvoiceMedium.fromCode(null));
        assertEquals(InvoiceMedium.OTHER, InvoiceMedium.fromCode(""));
        assertEquals(InvoiceMedium.OTHER, InvoiceMedium.fromCode("WATER"));
    }

    @Test
    public void codeRoundTrip() {
        for (InvoiceMedium medium : InvoiceMedium.values()) {
            assertEquals(medium, InvoiceMedium.fromCode(medium.getCode()));
        }
    }

    @Test
    public void i18nKeys() {
        assertEquals("plugin.accounting.invoices.medium.electricity", InvoiceMedium.ELECTRICITY.getI18nKey());
        assertEquals("plugin.accounting.invoices.medium.gas", InvoiceMedium.GAS.getI18nKey());
        assertEquals("plugin.accounting.invoices.medium.districtheating", InvoiceMedium.DISTRICT_HEATING.getI18nKey());
        assertEquals("plugin.accounting.invoices.medium.other", InvoiceMedium.OTHER.getI18nKey());
    }
}
