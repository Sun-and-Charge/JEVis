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

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import org.jevis.api.JEVisSample;
import org.jevis.api.JEVisType;
import org.jevis.jeconfig.plugin.meters.data.JEVisTypeWrapper;
import org.jevis.jeconfig.plugin.meters.data.MeterData;

public class DoubleColumn extends TableColumn<MeterData, String> {
    public DoubleColumn(JEVisType jeVisType, int width, String name) {
        super(name);
        try {

            setId(jeVisType.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.setCellValueFactory(param -> {
            if (param.getValue().getJeVisAttributeJEVisSampleMap().get(new JEVisTypeWrapper(jeVisType)) != null && param.getValue().getJeVisAttributeJEVisSampleMap().get(new JEVisTypeWrapper(jeVisType)).getOptionalJEVisSample().isPresent()) {
                try {
                    JEVisSample jeVisSample = param.getValue().getJeVisAttributeJEVisSampleMap().get(new JEVisTypeWrapper(jeVisType)).getOptionalJEVisSample().get();
                    return new SimpleStringProperty(String.format("%.2f", jeVisSample.getValueAsDouble()));
                } catch (Exception e) {
                    e.printStackTrace();
                    return new SimpleStringProperty();
                }
            }

            return new SimpleStringProperty();
        });

        this.setStyle("-fx-alignment: CENTER;");
        this.setMinWidth(width);


    }
}
