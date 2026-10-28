/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.driver.inputHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author bf
 */
public class SOAPMessageInputHandler extends InputHandler {
    private static final Logger logger = LogManager.getLogger(SOAPMessageInputHandler.class);

    public SOAPMessageInputHandler(List<SOAPMessage> input, Charset charset) {
        super(input, charset);
    }

    @Override
    public void convertInput() {

        List<SOAPMessage> input = (List<SOAPMessage>) _rawInput;
        for (SOAPMessage m : input) {
            try {
                _document.add(m.getSOAPBody().extractContentAsDocument());
            } catch (SOAPException ex) {
                logger.fatal(ex);
            }
        }
    }

}
