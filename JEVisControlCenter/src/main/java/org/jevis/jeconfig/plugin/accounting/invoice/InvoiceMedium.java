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

import org.jevis.commons.i18n.I18n;

import java.util.Locale;

/**
 * Medium of an energy invoice, as used by the invoice endpoints of the web service ({@code medium} parameter and
 * the {@code Medium} attribute of Energy Invoice and Energy Invoices Directory).
 */
public enum InvoiceMedium {
    ELECTRICITY("plugin.accounting.invoices.medium.electricity"),
    GAS("plugin.accounting.invoices.medium.gas"),
    DISTRICT_HEATING("plugin.accounting.invoices.medium.districtheating"),
    OTHER("plugin.accounting.invoices.medium.other");

    private final String i18nKey;

    InvoiceMedium(String i18nKey) {
        this.i18nKey = i18nKey;
    }

    /**
     * Maps the code of the web service to the medium; empty or unknown codes give {@link #OTHER}.
     */
    public static InvoiceMedium fromCode(String code) {
        if (code == null) {
            return OTHER;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (InvoiceMedium medium : values()) {
            if (medium.name().equals(normalized)) {
                return medium;
            }
        }
        return OTHER;
    }

    /**
     * @return the code sent to the web service, e.g. {@code DISTRICT_HEATING}
     */
    public String getCode() {
        return name();
    }

    public String getI18nKey() {
        return i18nKey;
    }

    /**
     * @return the translated name (de: Strom, Gas, Fernwärme, Sonstiges)
     */
    public String getDisplayName() {
        return I18n.getInstance().getString(i18nKey);
    }
}
