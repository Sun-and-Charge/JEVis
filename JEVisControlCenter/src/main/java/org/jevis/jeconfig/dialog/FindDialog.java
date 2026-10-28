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

import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Modality;
import org.jevis.api.JEVisDataSource;
import org.jevis.jeconfig.JEConfig;

import java.util.Optional;

/**
 *
 * @author fs
 */
public class FindDialog {

    public Response show(String title, String titleLong, String message) {
        Dialog dialog = new TextInputDialog("");
        dialog.initOwner(JEConfig.getStage());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(titleLong);
        dialog.setHeaderText("");

        Optional<String> result = dialog.showAndWait();
        String entered = "none.";

        if (result.isPresent()) {
            _result = result.get();
            return Response.YES;
        }
        return Response.NO;
    }

    private final JEVisDataSource _ds;
    private String _result = "";

    public FindDialog(JEVisDataSource ds) {
        _ds = ds;
    }

    public enum Response {

        NO, YES, CANCEL
    }

    public String getResult() {
        return _result;
    }

}
