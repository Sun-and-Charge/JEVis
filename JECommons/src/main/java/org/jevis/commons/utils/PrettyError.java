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
package org.jevis.commons.utils;

public class PrettyError {

    public static String getJEVisLineFilter(Exception ex) {
        try {
            for (StackTraceElement stackTraceElement : ex.getStackTrace()) {
                if (stackTraceElement.getFileName().startsWith("org.jevis")) {
                    String errorMsg = String.format("[%s] %s:%s - %s"
                            , stackTraceElement.getLineNumber()
                            , stackTraceElement.getClassName()
                            , stackTraceElement.getMethodName()
                            , ex.getMessage());
                    return errorMsg;
                }
            }
            return ex.getMessage();
        } catch (Exception exOut) {
            return ex.getMessage();
        }
    }
}
