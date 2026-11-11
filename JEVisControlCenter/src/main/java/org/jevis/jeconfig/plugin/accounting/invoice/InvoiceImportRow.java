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

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One file of the invoice import dialog: the answer of {@code POST /invoices/parse} (or the error) plus the choices
 * of the user (medium, import yes/no) and the result of the import.
 */
public class InvoiceImportRow {

    private final File file;
    private final String fileName;
    private final ObjectProperty<InvoiceMedium> medium = new SimpleObjectProperty<>(InvoiceMedium.OTHER);
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.ERROR);
    private String format;
    private String number;
    private LocalDate date;
    private String seller;
    private String sellerVatId;
    private String buyer;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String currency;
    private Double net;
    private Double tax;
    private Double gross;
    private Double consumption;
    private String consumptionUnit;
    private String meterPoint;
    private InvoiceMedium detectedMedium = InvoiceMedium.OTHER;
    private List<String> mediumReasons = Collections.emptyList();
    private List<String> warnings = Collections.emptyList();
    private Long duplicateId;
    private Long importedId;
    /**
     * Error text of the server (or of the client) for {@link Status#ERROR} and {@link Status#FAILED}.
     */
    private String message;
    /**
     * I18n key for a known error (file too large, no e-invoice, ...), else {@code null}.
     */
    private String messageKey;

    private InvoiceImportRow(File file, String fileName) {
        this.file = file;
        this.fileName = fileName;
    }

    /**
     * Builds a row from the answer of {@code POST /invoices/parse}.
     *
     * @param file the uploaded file
     * @param node the JSON answer
     */
    public static InvoiceImportRow fromParseResult(File file, JsonNode node) {
        String name = file != null ? file.getName() : text(node, "fileName");
        InvoiceImportRow row = new InvoiceImportRow(file, name);
        if (node == null || !node.isObject()) {
            row.message = "Empty answer";
            row.status.set(Status.ERROR);
            return row;
        }
        row.format = text(node, "format");
        row.number = text(node, "number");
        row.date = date(node, "date");
        row.seller = text(node, "seller");
        row.sellerVatId = text(node, "sellerVatId");
        row.buyer = text(node, "buyer");
        row.periodStart = date(node, "periodStart");
        row.periodEnd = date(node, "periodEnd");
        row.currency = text(node, "currency");
        row.net = number(node, "net");
        row.tax = number(node, "tax");
        row.gross = number(node, "gross");
        JsonNode consumption = node.get("consumption");
        if (consumption != null && consumption.isObject()) {
            row.consumption = number(consumption, "value");
            row.consumptionUnit = text(consumption, "unit");
        }
        row.meterPoint = text(node, "meterPoint");
        row.detectedMedium = InvoiceMedium.fromCode(text(node, "medium"));
        row.medium.set(row.detectedMedium);
        row.mediumReasons = texts(node, "mediumReasons");
        row.warnings = texts(node, "warnings");
        JsonNode duplicate = node.get("duplicate");
        if (duplicate != null && !duplicate.isNull() && duplicate.canConvertToLong() && duplicate.asLong() > 0) {
            row.duplicateId = duplicate.asLong();
        } else if (duplicate != null && duplicate.isTextual()) {
            try {
                row.duplicateId = Long.parseLong(duplicate.asText().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (row.duplicateId != null) {
            row.status.set(Status.DUPLICATE);
            row.selected.set(false);
        } else {
            row.status.set(Status.NEW);
            row.selected.set(true);
        }
        return row;
    }

    /**
     * Builds a row for a file that could not be parsed.
     *
     * @param file       the file
     * @param httpStatus HTTP status of the answer, 0 if there was none
     * @param errorCode  {@code error} of the answer, may be {@code null}
     * @param message    message of the server or the exception
     */
    public static InvoiceImportRow fromError(File file, int httpStatus, String errorCode, String message) {
        InvoiceImportRow row = new InvoiceImportRow(file, file != null ? file.getName() : "");
        row.status.set(Status.ERROR);
        row.selected.set(false);
        row.message = message;
        row.messageKey = errorKey(httpStatus, errorCode);
        return row;
    }

    /**
     * I18n key for a known error of the invoice endpoints, or {@code null} if the message of the server should be
     * shown.
     */
    public static String errorKey(int httpStatus, String errorCode) {
        if ("NOT_AN_E_INVOICE".equals(errorCode)) return "plugin.accounting.invoices.error.noeinvoice";
        if ("UNREADABLE".equals(errorCode)) return "plugin.accounting.invoices.error.unreadable";
        if ("DUPLICATE".equals(errorCode) || httpStatus == 409) return "plugin.accounting.invoices.error.duplicate";
        switch (httpStatus) {
            case 401:
                return "plugin.accounting.invoices.error.unauthorized";
            case 403:
                return "plugin.accounting.invoices.error.forbidden";
            case 404:
            case 405:
                return "plugin.accounting.invoices.error.notsupported";
            case 413:
                return "plugin.accounting.invoices.error.toolarge";
            default:
                return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String s = value.asText();
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static Double number(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (value.isNumber()) return value.asDouble();
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static LocalDate date(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static List<String> texts(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        for (JsonNode item : value) {
            if (item != null && !item.isNull()) list.add(item.asText());
        }
        return list;
    }

    /**
     * @return {@code true} if the file can be sent to the import (it was parsed, also for duplicates)
     */
    public boolean isImportable() {
        return status.get() == Status.NEW || status.get() == Status.DUPLICATE;
    }

    /**
     * The import created the invoice.
     */
    public void markImported(Long invoiceId) {
        this.importedId = invoiceId;
        this.selected.set(false);
        this.status.set(Status.IMPORTED);
    }

    /**
     * The import found an invoice with the same seller and number.
     */
    public void markDuplicate(Long existingId) {
        if (existingId != null) this.duplicateId = existingId;
        this.selected.set(false);
        this.status.set(Status.SKIPPED);
    }

    /**
     * The import failed.
     */
    public void markFailed(int httpStatus, String errorCode, String message) {
        this.message = message;
        this.messageKey = errorKey(httpStatus, errorCode);
        this.selected.set(false);
        this.status.set(Status.FAILED);
    }

    public File getFile() {
        return file;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFormat() {
        return format;
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getSeller() {
        return seller;
    }

    public String getSellerVatId() {
        return sellerVatId;
    }

    public String getBuyer() {
        return buyer;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getCurrency() {
        return currency;
    }

    public Double getNet() {
        return net;
    }

    public Double getTax() {
        return tax;
    }

    public Double getGross() {
        return gross;
    }

    public Double getConsumption() {
        return consumption;
    }

    public String getConsumptionUnit() {
        return consumptionUnit;
    }

    public String getMeterPoint() {
        return meterPoint;
    }

    public InvoiceMedium getDetectedMedium() {
        return detectedMedium;
    }

    public List<String> getMediumReasons() {
        return mediumReasons;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public Long getDuplicateId() {
        return duplicateId;
    }

    public Long getImportedId() {
        return importedId;
    }

    public String getMessage() {
        return message;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public InvoiceMedium getMedium() {
        return medium.get();
    }

    public void setMedium(InvoiceMedium medium) {
        this.medium.set(medium == null ? InvoiceMedium.OTHER : medium);
    }

    public ObjectProperty<InvoiceMedium> mediumProperty() {
        return medium;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public Status getStatus() {
        return status.get();
    }

    public ObjectProperty<Status> statusProperty() {
        return status;
    }

    public enum Status {
        /**
         * parsed, not yet in the directory
         */
        NEW,
        /**
         * parsed, an invoice with the same seller and number exists
         */
        DUPLICATE,
        /**
         * could not be parsed
         */
        ERROR,
        /**
         * imported
         */
        IMPORTED,
        /**
         * not imported because the import reported a duplicate
         */
        SKIPPED,
        /**
         * the import failed
         */
        FAILED
    }
}
