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

import java.io.*;
import java.nio.charset.Charset;

/**
 * @author Broder
 */
public class FileInputHandler extends InputHandler {
    private static final Logger logger = LogManager.getLogger(FileInputHandler.class);

    public FileInputHandler(File file, Charset charset) {
        super(file, charset);
    }

    //input is file
    @Override
    public void convertInput() {
        logger.info("--Convert Array Input--");
        try {
            File file = (File) _rawInput;
            FileReader reader = null;
            reader = new FileReader(file);
            BufferedReader buffer = new BufferedReader(reader);
            String line = buffer.readLine();
            while (line != null) {
                _inputStream.add(new ByteArrayInputStream(line.getBytes()));
                line = buffer.readLine();
            }
        } catch (IOException ex) {
            logger.fatal(ex);
        }
    }
}
