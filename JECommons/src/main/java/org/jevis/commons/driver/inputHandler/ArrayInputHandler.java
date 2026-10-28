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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author Broder
 */
public class ArrayInputHandler extends InputHandler {
    private static final Logger logger = LogManager.getLogger(ArrayInputHandler.class);

    public ArrayInputHandler(Object[] input, Charset charset) {
        super(input, charset);
    }

    //rawInput ist Object[]
    @Override
    public void convertInput() {
        logger.info("--Convert Array Input--");
        for (int i = 0; i < ((List) _rawInput).size(); i++) {
            StringBuilder builder = new StringBuilder();
            Object[] o = (Object[]) ((List) _rawInput).get(i);
            for (int j = 0; j < o.length; j++) {
                String s = (String) o[j];
                builder.append(s);
            }
            _inputStream.add(new ByteArrayInputStream(builder.toString().getBytes()));
        }
    }
}
