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
public class MeterQrDialog extends org.jevis.jeconfig.application.ui.SucDialog<ButtonType> {
    public MeterQrDialog(MeterData meterData, JEVisDataSource ds) {
        super(I18n.getInstance().getString("plugin.meters.qr.title") + " – " + meterData.getName(), null, Size.M);
        getDialogPane().setContent(new MeterQrPane(meterData, ds));
        ButtonType close = new ButtonType(org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.close", "Schließen", "Close"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().add(close);
        ((javafx.scene.control.Button) getDialogPane().lookupButton(close)).setDefaultButton(true);
    }
}
