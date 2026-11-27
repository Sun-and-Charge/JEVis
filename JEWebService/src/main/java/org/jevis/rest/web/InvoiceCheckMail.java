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

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/**
 * German HTML mail about a deviation found by the invoice check (R03).
 */
final class InvoiceCheckMail {

    private InvoiceCheckMail() {
    }

    static String html(InvoiceCheck.Invoice invoice, Map<String, Object> result) {
        StringBuilder html = new StringBuilder("<html><body style=\"font-family:sans-serif\">");
        html.append("<p>Die automatische Rechnungsprüfung hat eine Abweichung gefunden.</p><table cellpadding=\"4\">");
        row(html, "Rechnung", invoice.number != null ? invoice.number : "#" + invoice.id);
        row(html, "Lieferant", invoice.seller);
        row(html, "Zählpunkt / Marktlokation", invoice.meterPoint);
        Object contract = result.get("contract");
        if (contract instanceof Map) row(html, "Vertrag (Kostenrechnung)", String.valueOf(((Map<?, ?>) contract).get("name")));
        html.append("</table>");
        part(html, "Menge", result.get("quantity"), invoice.consumptionUnit, "gemessen", "laut Rechnung");
        part(html, "Nettobetrag", result.get("amount"), "€", "laut Vertrag", "laut Rechnung");
        html.append("<p>Die Rechnung und die Prüfung finden Sie in der Web-App unter Finanzen → Energierechnungen.</p>");
        html.append("</body></html>");
        return html.toString();
    }

    private static void part(StringBuilder html, String title, Object value, String unit, String expectedLabel, String invoicedLabel) {
        if (!(value instanceof Map)) return;
        Map<?, ?> part = (Map<?, ?>) value;
        boolean ok = Boolean.TRUE.equals(part.get("ok"));
        html.append("<h3 style=\"margin:12px 0 4px\">").append(escape(title)).append(ok ? " – in Ordnung" : " – Abweichung").append("</h3>");
        html.append("<table cellpadding=\"4\">");
        String suffix = unit != null && !unit.isEmpty() ? " " + unit : "";
        row(html, expectedLabel, number(part.get("expected")) + suffix);
        row(html, invoicedLabel, number(part.get("invoiced")) + suffix);
        Object percent = part.get("deviationPercent");
        row(html, "Abweichung", number(part.get("difference")) + suffix + (percent != null ? " (" + number(percent) + " %)" : ""));
        row(html, "Toleranz", number(part.get("tolerancePercent")) + " %");
        html.append("</table>");
    }

    private static void row(StringBuilder html, String label, String value) {
        if (value == null || value.isEmpty()) return;
        html.append("<tr><td style=\"color:#555\">").append(escape(label)).append("</td><td>").append(escape(value)).append("</td></tr>");
    }

    static String number(Object value) {
        if (!(value instanceof Number)) return "–";
        NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMANY);
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(0);
        return format.format(((Number) value).doubleValue());
    }

    static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
