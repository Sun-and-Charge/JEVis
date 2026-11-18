/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.application.control;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

/**
 * Check of an input field (replaces the JFoenix {@code ValidatorBase}, J25): a subclass sets {@link #hasErrors} in
 * {@link #eval()} from the {@link #srcControl}; {@link #validate(Node)} marks the field with the pseudo class
 * {@code :error} and shows the message as tooltip.
 */
public abstract class TextValidator {
    public static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    protected final ObjectProperty<Node> srcControl = new SimpleObjectProperty<>();
    protected final BooleanProperty hasErrors = new SimpleBooleanProperty(false);
    private String message = "";

    protected abstract void eval();

    /** @return true if the value of the control is valid */
    public boolean validate(Node control) {
        srcControl.set(control);
        eval();
        boolean error = hasErrors.get();
        control.pseudoClassStateChanged(ERROR, error);
        if (control instanceof Control) {
            ((Control) control).setTooltip(error && !message.isEmpty() ? new Tooltip(message) : null);
        }
        return !error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    public BooleanProperty hasErrorsProperty() {
        return hasErrors;
    }
}
