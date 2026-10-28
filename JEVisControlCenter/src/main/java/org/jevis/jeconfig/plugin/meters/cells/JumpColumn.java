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
package org.jevis.jeconfig.plugin.meters.cells;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TableColumn;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisType;
import org.jevis.jeconfig.application.table.JumpCell;
import org.jevis.jeconfig.plugin.meters.data.JEVisTypeWrapper;
import org.jevis.jeconfig.plugin.meters.data.MeterData;
import org.jevis.jeconfig.plugin.meters.data.SampleData;

public class JumpColumn extends TableColumn<MeterData, SampleData> {

    public JumpColumn(String s, JEVisType jeVisType, int width, JEVisDataSource ds) {
        super(s);
        try {

            setId(jeVisType.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.setCellValueFactory(meterDataOptionalCellDataFeatures -> {
            return new SimpleObjectProperty<>(meterDataOptionalCellDataFeatures.getValue().getJeVisAttributeJEVisSampleMap().get(new JEVisTypeWrapper(jeVisType)));
        });

        this.setCellFactory(new JumpCell<>(ds));
        this.setMinWidth(width);

    }
}
