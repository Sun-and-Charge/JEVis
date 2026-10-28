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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import javax.xml.soap.SOAPMessage;

/**
 *
 * @author Broder
 */
public class InputHandlerFactory {
    
    public static InputHandler getInputConverter(Object input) {
        Charset charset = Charset.defaultCharset();
        return getInputConverter(input, charset);
    }

    public static InputHandler getInputConverter(Object input, Charset charset) {
        if (input instanceof List) {
            List tmp = (List) input;
            if (tmp.isEmpty()) {
                return null;
            }
            Object o = tmp.get(0);
            if (o instanceof String) {
                return new StringInputHandler((List<String>) input, charset);
            }

            if (o instanceof List) {
                return new StringInputHandler((List<String>) o, charset);
            }

            if (o instanceof SOAPMessage) {
                return new SOAPMessageInputHandler((List<SOAPMessage>) input, charset);
            }
            return null;
        } else if (input instanceof Object[]) {
            return new ArrayInputHandler((Object[]) input, charset);
        } else if (input instanceof File) {
            return new FileInputHandler((File) input, charset);
        } else if (input instanceof InputStream) {
            return new InputStreamHandler((InputStream) input, charset);
        }
        return null;
    }
}
