/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.tool;

public class Exceptions {

    public static String toString(Exception ex) {
        String message = "";

        try {
            message += "\n" + ex.getMessage() + " at: ";

        } catch (Exception ex2) {

        }

        try {
            boolean foundFirstInJEVis = false;
            StackTraceElement[] stack = ex.getStackTrace();
            if (stack.length > 0) {
                for (StackTraceElement stackTraceElement : stack) {
                    if (stackTraceElement.getClassName().startsWith("org.jevis") && !foundFirstInJEVis) {
                        message += stackTraceElement.toString();
                        foundFirstInJEVis = true;
                    }
                }
                if (!foundFirstInJEVis) {
                    message += stack[0].toString();
                }

            }
        } catch (Exception ex3) {

        }

        return message;
    }
}
