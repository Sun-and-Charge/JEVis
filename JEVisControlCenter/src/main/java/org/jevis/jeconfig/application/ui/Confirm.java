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
package org.jevis.jeconfig.application.ui;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

/**
 * Confirmations (docs/DESIGN-CC.md): the title is a question naming action and object („Analyse „Strom 2026“
 * löschen?“), the text says consequence and scope, the action button repeats the verb („Löschen“) instead of OK/Ja.
 */
public final class Confirm {

    private Confirm() {
    }

    /**
     * @param question    title, e.g. „Analyse „Strom 2026“ löschen?“
     * @param consequence what happens, e.g. „Die Analyse kommt in den Papierkorb.“
     * @param action      verb of the action button, e.g. „Löschen“
     * @param destructive red action button (delete, discard)
     * @return true when the action was chosen
     */
    public static boolean ask(String question, String consequence, String action, boolean destructive) {
        SucDialog<ButtonType> dialog = new SucDialog<>(question, null, SucDialog.Size.S);
        if (consequence != null && !consequence.isEmpty()) {
            Label text = new Label(consequence);
            text.setWrapText(true);
            text.getStyleClass().add("suc-dialog-text");
            dialog.getDialogPane().setContent(text);
        }
        /* a destructive action is no default button (Enter must not delete); OTHER keeps it left of "Abbrechen" */
        ButtonType actionType = new ButtonType(action, destructive ? ButtonBar.ButtonData.OTHER : ButtonBar.ButtonData.OK_DONE);
        if (destructive) {
            dialog.addDestructive(actionType);
        } else {
            dialog.addPrimary(actionType);
        }
        dialog.addCancel();
        return dialog.showAndWait().map(result -> result == actionType).orElse(false);
    }
}
