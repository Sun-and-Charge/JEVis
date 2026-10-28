/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of MSCONSParser.
 * <p>
 * MSCONSParser is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * MSCONSParser is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * MSCONSParser. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * MSCONSParser is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.mscons;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class InterchangeHeader {
    private String syntaxIdentifier;
    private String sender;
    private String recipient;

    private String date;

    private String time;

    private String controlReference;

    private String applicationReference;





    private DateTime createdDateTime;


    public String getSyntaxIdentifier() {
        return syntaxIdentifier;
    }

    public void setSyntaxIdentifier(String syntaxIdentifier) {
        this.syntaxIdentifier = syntaxIdentifier;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public DateTime getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(DateTime createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public void genearteDateTime() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyMMdd HHmm");
       createdDateTime = dateTimeFormatter.parseDateTime(date + " " + time);

    }


    public void setDate(String date) {
        this.date = date;
    }


    public void setTime(String time) {
        this.time = time;
    }

    public String getControlReference() {
        return controlReference;
    }

    public void setControlReference(String controlReference) {
        this.controlReference = controlReference;
    }

    public String getApplicationReference() {
        return applicationReference;
    }

    public void setApplicationReference(String applicationReference) {
        this.applicationReference = applicationReference;
    }


    @Override
    public String toString() {
        return "InterchangeHeader{" +
                "SyntaxIdentifier='" + syntaxIdentifier + '\'' +
                ", Sender='" + sender + '\'' +
                ", Recipient='" + recipient + '\'' +
                ", date='" + date + '\'' +
                ", time='" + time + '\'' +
                ", controlReference='" + controlReference + '\'' +
                ", applicationReference='" + applicationReference + '\'' +
                ", dateTime=" + createdDateTime +
                '}';
    }
}
