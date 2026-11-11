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
package org.jevis.rest.invoice;

/**
 * Medium of an energy invoice; the value of the attribute "Medium" of the invoice and of its medium folder
 * (see docs/ENERGY-INVOICES.md).
 */
public enum Medium {
    ELECTRICITY("Strom", "Electricity"),
    GAS("Gas", "Gas"),
    DISTRICT_HEATING("Fernwärme", "District heating"),
    OTHER("Sonstiges", "Other");

    private final String nameDe;
    private final String nameEn;

    Medium(String nameDe, String nameEn) {
        this.nameDe = nameDe;
        this.nameEn = nameEn;
    }

    /** @return German name, used as object name of the medium folder */
    public String nameDe() {
        return nameDe;
    }

    /** @return English name, local name "en" of the medium folder */
    public String nameEn() {
        return nameEn;
    }

    /**
     * @return the medium of the value (case-insensitive) or {@code null} if it is no medium
     */
    public static Medium parse(String value) {
        if (value == null) return null;
        for (Medium medium : values()) {
            if (medium.name().equalsIgnoreCase(value.trim())) return medium;
        }
        return null;
    }
}
