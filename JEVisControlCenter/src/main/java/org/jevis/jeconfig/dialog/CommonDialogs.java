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
package org.jevis.jeconfig.dialog;

import javafx.scene.control.TextArea;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.jevis.jeconfig.JEConfig;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 *
 * @author fs
 */
public abstract class CommonDialogs {

    /**
     *
     * @param titel
     * @param header
     * @param ex
     */
    public static void showError(String titel, String header, String message, Exception ex) {
        /* J25.5: an error that stays, with "Details" (stack trace, copyable) instead of an error window */
        String text = header != null && !header.isEmpty() ? header : titel;
        if (message != null && !message.isEmpty()) text += ": " + message;
        else if (ex != null && ex.getMessage() != null) text += " (" + ex.getMessage() + ")";
        org.jevis.jeconfig.application.ui.Notify.error(text, ex);
    }
}
