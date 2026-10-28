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

import java.util.ArrayList;
import java.util.List;

public class MsconsPojo {
    private InterchangeHeader interchangeHeader = new InterchangeHeader();
    private MessageHeader messageHeader = new MessageHeader();
    private List<Messlokation> messlokation = new ArrayList<>();


    public InterchangeHeader getInterchangeHeader() {
        return interchangeHeader;
    }

    public void setInterchangeHeader(InterchangeHeader interchangeHeader) {
        this.interchangeHeader = interchangeHeader;
    }

    public MessageHeader getMessageHeader() {
        return messageHeader;
    }

    public void setMessageHeader(MessageHeader messageHeader) {
        this.messageHeader = messageHeader;
    }

    public List<Messlokation> getMesslokation() {
        return messlokation;
    }

    public Messlokation getLastMesslokation() {
      return   messlokation.get(messlokation.size() - 1);
    }

    public void setMesslokation(List<Messlokation> messlokation) {
        this.messlokation = messlokation;
    }

    @Override
    public String toString() {
        return "MsconsPojo{" +
                "interchangeHeader=" + interchangeHeader +
                ", messageHeader=" + messageHeader +
                ", messlokation=" + messlokation +
                '}';
    }
}
