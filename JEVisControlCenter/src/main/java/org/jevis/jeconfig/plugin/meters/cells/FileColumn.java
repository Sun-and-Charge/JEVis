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
import org.jevis.api.JEVisSample;
import org.jevis.api.JEVisType;
import org.jevis.jeconfig.application.table.FileCell;
import org.jevis.jeconfig.plugin.meters.data.JEVisTypeWrapper;
import org.jevis.jeconfig.plugin.meters.data.MeterData;

import java.util.Optional;

public class FileColumn extends TableColumn<MeterData, Optional<JEVisSample>> {
    JEVisType jeVisType;
    int width;

    public FileColumn(JEVisType jeVisType, int width, String name) {
        super(name);
        try {

            setId(jeVisType.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }


        this.setCellValueFactory(param -> {
            Optional<JEVisSample> jeVisSample;
            if (param.getValue().getJeVisAttributeJEVisSampleMap().get(new JEVisTypeWrapper(jeVisType)) == null) {
                jeVisSample = Optional.empty();
            } else {
                jeVisSample = param.getValue().getJeVisAttributeJEVisSampleMap().get(new JEVisTypeWrapper(jeVisType)).getOptionalJEVisSample();
            }
            try {
                return new SimpleObjectProperty<Optional<JEVisSample>>(jeVisSample);
            } catch (Exception e) {
                e.printStackTrace();
                return new SimpleObjectProperty<Optional<JEVisSample>>(Optional.empty());
            }


        });

        this.setCellFactory(new FileCell<>());
        this.setStyle("-fx-alignment: CENTER;");
        this.setMinWidth(width);


    }
}
