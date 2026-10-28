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
package org.jevis.jeconfig.tool.template;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Templates {

    public static ObservableList<Template> getAllTemplates() {
        ObservableList templateList = FXCollections.observableArrayList();

        templateList.add(new EnergyMeterCounter());
        templateList.add(new EnergyMeterKWH());
        templateList.add(new EnergyMeterKW());
        templateList.add(new VolumeMeterCounterCubic());
        templateList.add(new VolumeMeterCubic());
        templateList.add(new VolumeMeterCounterLiter());
        templateList.add(new VolumeMeterLiter());
        templateList.add(new Temperature());
        templateList.add(new BuildingObject());

        return templateList;
    }

}
