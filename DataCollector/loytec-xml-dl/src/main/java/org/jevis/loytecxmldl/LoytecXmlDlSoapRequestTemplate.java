/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of loytec-xml-dl.
 * <p>
 * loytec-xml-dl is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * loytec-xml-dl is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * loytec-xml-dl. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * loytec-xml-dl is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.loytecxmldl;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.jevis.loytecxmldl.LoytecXmlDlServerClass.NUMBER_OF_SAMPLES_PER_REQUEST;

/**
 *
 */
public class LoytecXmlDlSoapRequestTemplate {

    private final static Logger log = LogManager.getLogger(LoytecXmlDlSoapRequestTemplate.class.getName());
    private final String technology;
    private final String trendId;
    private String logHandle;

    public LoytecXmlDlSoapRequestTemplate(LoytecXmlDlChannelDirectory channelDirectory, LoytecXmlDlChannel channel) {

        technology = channelDirectory.getTechnology();
        trendId = channel.getTrendId();
    }

    public String getTemplate() {
        return "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "<SOAP-ENV:Header/>\n" +
                "<SOAP-ENV:Body>\n" +
                "<LogRead xmlns=\"http://www.loytec.com/wsdl/XMLDL/1.0/\" " +
                "NumItems=\"" + NUMBER_OF_SAMPLES_PER_REQUEST + "\" " +
                "ReturnCompleteSet=\"false\" " +
                "StartDateTime=\"${DF:yyyy-MM-dd'T'HH:mm:ss}\">\n" +
                "<ReqBase logHandle=\"" + logHandle + "\"/>\n" +
                "</LogRead>\n" +
                "</SOAP-ENV:Body>\n" +
                "</SOAP-ENV:Envelope>";
    }

    public void setLogHandle(String basePath) {
        logHandle = basePath + technology + "/trend-" + trendId + ".bin";
    }
}
