/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEAPI-WS.
 * <p>
 * JEAPI-WS is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEAPI-WS is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEAPI-WS. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEAPI-WS is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeapi.ws;

import org.jevis.api.JEVisException;

/**
 * Error answer of the invoice endpoints ({@code /invoices/parse}, {@code /invoices/import}).
 * <p>
 * {@link #getHttpStatus()} is the HTTP status (400, 401, 403, 409, 413, ...), {@link #getErrorCode()} the
 * {@code error} field of the answer (e.g. {@code NOT_AN_E_INVOICE}, {@code UNREADABLE}, {@code DUPLICATE}) or
 * {@code null}, {@link #getExistingId()} the id of the existing invoice for {@code DUPLICATE}. A status of 0 means
 * the server could not be reached.
 */
public class InvoiceRequestException extends JEVisException {

    public static final String NOT_AN_E_INVOICE = "NOT_AN_E_INVOICE";
    public static final String UNREADABLE = "UNREADABLE";
    public static final String DUPLICATE = "DUPLICATE";

    private final int httpStatus;
    private final String errorCode;
    private final Long existingId;

    public InvoiceRequestException(int httpStatus, String errorCode, String message, Long existingId) {
        super(message, httpStatus);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.existingId = existingId;
    }

    public InvoiceRequestException(String message, Throwable cause) {
        super(message, 0, cause);
        this.httpStatus = 0;
        this.errorCode = null;
        this.existingId = null;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Long getExistingId() {
        return existingId;
    }

    /**
     * @return {@code true} if the server reported an invoice with the same seller and number
     */
    public boolean isDuplicate() {
        return httpStatus == 409 || DUPLICATE.equals(errorCode);
    }
}
