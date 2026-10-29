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
package org.jevis.jeconfig.plugin.meters.ui;

import javafx.scene.control.Tab;
import org.jevis.api.JEVisDataSource;
import org.jevis.jeconfig.plugin.meters.data.MeterData;
import org.jevis.jeconfig.plugin.meters.qr.MeterQrPane;

/**
 * Read-only tab of the meter form with the meter's QR code.
 */
public class MeterFormQrTab extends Tab implements MeterFormTab {

    public MeterFormQrTab(MeterData meterData, JEVisDataSource ds, String name) {
        super(name, new MeterQrPane(meterData, ds));
    }

    @Override
    public void commit() {
    }
}
