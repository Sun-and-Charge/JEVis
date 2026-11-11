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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error of the invoice endpoints with HTTP status and the error code of the answer
 * ({@code {"error": code, "message": ...}}, see docs/ENERGY-INVOICES.md).
 */
public class InvoiceException extends Exception {

    public static final String NOT_AN_E_INVOICE = "NOT_AN_E_INVOICE";
    public static final String UNREADABLE = "UNREADABLE";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String INVALID_MEDIUM = "INVALID_MEDIUM";
    public static final String INVALID_DIRECTORY = "INVALID_DIRECTORY";
    public static final String TOO_LARGE = "TOO_LARGE";
    public static final String NO_FILE = "NO_FILE";

    private final int status;
    private final String code;
    private final Long existing;

    public InvoiceException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public InvoiceException(int status, String code, String message, Long existing) {
        super(message);
        this.status = status;
        this.code = code;
        this.existing = existing;
    }

    /** 400: the file is no e-invoice */
    static InvoiceException notAnEInvoice(String message) {
        return new InvoiceException(400, NOT_AN_E_INVOICE, message);
    }

    /** 400: the file is damaged or the invoice cannot be read */
    static InvoiceException unreadable(String message) {
        return new InvoiceException(400, UNREADABLE, message);
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** @return id of the existing invoice for {@link #DUPLICATE}, else null */
    public Long getExisting() {
        return existing;
    }

    /** @return the JSON answer */
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("error", code);
        if (existing != null) json.put("existing", existing);
        json.put("message", getMessage());
        return json;
    }
}
