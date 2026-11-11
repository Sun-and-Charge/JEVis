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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The values read from an electronic invoice ({@link InvoiceReader}), before the energy specific analysis
 * ({@link InvoiceAnalysis}). Plain data, {@code null} if not present in the invoice.
 */
public class EInvoice {

    /** e.g. "ZUGFeRD/Factur-X EN16931", "XRechnung 3.0 UBL" */
    public String format;
    public String number;
    public LocalDate date;
    public String seller;
    public String sellerVatId;
    public String buyer;
    /** billing period of the header (not of the lines) */
    public LocalDate periodStart;
    public LocalDate periodEnd;
    public String currency;
    public BigDecimal net;
    public BigDecimal tax;
    public BigDecimal gross;
    /** true for a credit note (UBL CreditNote, type code 381) */
    public boolean creditNote;
    /** free texts of the header (notes) */
    public final List<String> notes = new ArrayList<>();
    /** identifiers and names of the delivery location (ship-to party) */
    public final List<String> deliveryTexts = new ArrayList<>();
    public final List<Line> lines = new ArrayList<>();
    /** problems found while reading, shown to the user */
    public final List<String> warnings = new ArrayList<>();
    /** the invoice XML (embedded in the PDF or the file itself) */
    public String xml;

    /**
     * One invoice line.
     */
    public static class Line {
        public String name;
        public String description;
        public BigDecimal quantity;
        /** UN/ECE Recommendation 20 unit code, e.g. KWH, MTQ, MON */
        public String unitCode;
        public BigDecimal unitPrice;
        public BigDecimal net;
        public BigDecimal taxRate;
        public LocalDate periodStart;
        public LocalDate periodEnd;
        /** notes of the line and product attributes ("name: value") */
        public final List<String> texts = new ArrayList<>();

        /** @return name, else description, else "" */
        public String label() {
            if (name != null && !name.trim().isEmpty()) return name.trim();
            return description != null ? description.trim() : "";
        }
    }
}
