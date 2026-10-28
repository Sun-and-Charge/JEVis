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
package org.jevis.jeconfig.plugin.object.attribute;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisSample;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.Boxes.TimeZoneBox;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;


/**
 * @author br
 */
public class TimeZoneEditor implements AttributeEditor {
    private static final Logger logger = LogManager.getLogger(TimeZoneEditor.class);

    private final JEVisAttribute _attribute;
    private final HBox _editor = new HBox(5);
    private final BooleanProperty _changed = new SimpleBooleanProperty(false);
    private final BooleanProperty _readOnly = new SimpleBooleanProperty(true);
    private JEVisSample _newSample;

    public TimeZoneEditor(JEVisAttribute att) {
        _editor.getStylesheets().add("/styles/TimeZoneEditor.css");
        _attribute = att;
        buildGUI();
    }

    private void buildGUI() {
        JEVisSample sample = _attribute.getLatestSample();

        DateTimeZone utc = DateTimeZone.UTC;
        TimeZoneBox timeZoneBox = new TimeZoneBox();

        if (sample != null) {
            try {
                String value = sample.getValueAsString();
                DateTimeZone dateTimeZone = null;

                try {
                    dateTimeZone = DateTimeZone.forID(value);
                } catch (IllegalArgumentException e) {

                }

                if (dateTimeZone != null) {
                    timeZoneBox.getSelectionModel().select(dateTimeZone);
                } else {
                    //Fix missconfigured zones
                    timeZoneBox.getSelectionModel().select(utc);

                    _newSample = _attribute.buildSample(new DateTime(), utc.getID());
                    _changed.setValue(Boolean.TRUE);

                }

            } catch (JEVisException ex) {
                logger.fatal(ex);
            }
        } else {
            try {
                timeZoneBox.getSelectionModel().select(utc);
                _newSample = _attribute.buildSample(new DateTime(), utc.getID());
                _changed.setValue(Boolean.TRUE);
            } catch (Exception ex) {
                logger.fatal(ex);
            }
        }

        _readOnly.addListener((observable, oldValue, newValue) -> timeZoneBox.setDisable(newValue));

        timeZoneBox.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            try {
                _newSample = _attribute.buildSample(new DateTime(), newValue);
                _changed.setValue(Boolean.TRUE);
            } catch (JEVisException ex) {
                logger.fatal(ex);
            }
        });
        _editor.getChildren().setAll(timeZoneBox);

    }

    @Override
    public boolean hasChanged() {
        return _changed.getValue();
    }

    @Override
    public void commit() throws JEVisException {
        if (_newSample != null) {
            _newSample.commit();
            _changed.setValue(false);
        }
    }

    @Override
    public void update() {
        Platform.runLater(() -> {
            _editor.getChildren().clear();
            buildGUI();
        });
    }

    @Override
    public Node getEditor() {
        return _editor;
    }

    @Override
    public BooleanProperty getValueChangedProperty() {
        return _changed;
    }

    @Override
    public void setReadOnly(boolean canRead) {
        _readOnly.setValue(canRead);
    }

    @Override
    public JEVisAttribute getAttribute() {
        return _attribute;
    }

    @Override
    public boolean isValid() {
        //TODO: implement validation
        return true;
    }
}
