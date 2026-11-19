/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.application.tools.WindowSize;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionDialog2 {

    public static void showException(Window parent, Exception ex) {

        String header = ex.toString();
        String cause = ex.toString();
        if (ex.getCause() != null) {
            cause = ex.getCause().getMessage();
        }

        showException(parent,
                I18n.getInstance().getString("dialog.error.title"),
                header, cause, ex);
    }

    public static void showException(Window parent, String title, String header, String content, Exception ex) {
        /* J25.5: an error that stays, with "Details" (stack trace, copyable) instead of an error window */
        String text = header != null && !header.isEmpty() ? header : title;
        if (content != null && !content.isEmpty()) text += ": " + content;
        org.jevis.jeconfig.application.ui.Notify.error(text, ex);
    }

}
