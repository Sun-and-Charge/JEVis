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
package org.jevis.jeconfig.plugin.scada;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import org.jevis.api.JEVisAttribute;
import org.jevis.jeconfig.application.jevistree.UserSelection;
import org.jevis.jeconfig.plugin.scada.data.ScadaElementData;

/**
 * The interface Scada element.
 */
public interface SCADAElement {


    ScadaElementData getData();

    void setData(ScadaElementData data);

    StringProperty titleProperty();

    void setAttribute(JEVisAttribute att);

    DoubleProperty xPositionProperty();

    DoubleProperty yPositionProperty();

    Node getGraphic();

    void update();

    UserSelection getUserSeclection();

    double getRelativeXPos();

    double getRelativeYPos();

    void setParent(Pane parent);

    BooleanProperty movableProperty();

    void openConfig();
}
