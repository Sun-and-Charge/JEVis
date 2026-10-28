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

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author Broder
 */
public class StringInputHandler extends InputHandler {
    private static final Logger logger = LogManager.getLogger(StringInputHandler.class);

    public StringInputHandler(List input, Charset charset) {
        super(input, charset);
    }
    //input is List<List<String>>

    @Override
    public void convertInput() {
        logger.info("--convertiere String input--");
        List<String> input = (List<String>) _rawInput;
        for (String o : input) {
//            List tmp = (List) o;
//            for (Object m : tmp) {
//                String s = (String) m;
//            logger.info("Value beim convert "+m);
            _inputStream.add(new ByteArrayInputStream(o.getBytes()));
//            }
        }
        logger.info("Inputstream size " + _inputStream.size());
    }
}
