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
package org.jevis.jeconfig.plugin.meters.qr;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import org.jevis.api.JEVisDataSource;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.plugin.meters.data.MeterData;

/**
 * Dialog with the QR code of one meter.
 */
public class MeterQrDialog extends Dialog<ButtonType> {

    public MeterQrDialog(MeterData meterData, JEVisDataSource ds) {
        initOwner(JEConfig.getStage());
        setTitle(I18n.getInstance().getString("plugin.meters.qr.title"));
        setHeaderText(null);
        setResizable(true);
        TopMenu.applyActiveTheme(((Stage) getDialogPane().getScene().getWindow()).getScene());

        getDialogPane().setContent(new MeterQrPane(meterData, ds));
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }
}
