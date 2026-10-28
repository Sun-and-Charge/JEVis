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
package org.jevis.jeconfig.plugin.dashboard.config2;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.ImageView;
import org.jevis.jeconfig.plugin.dashboard.datahandler.DataModelWidget;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

public class TimeFrameWidgetObject {

    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    private final ObjectProperty<Start> startObjectProperty = new SimpleObjectProperty<>(Start.NONE);
    private final ObjectProperty<End> endObjectProperty = new SimpleObjectProperty<>(End.NONE);

    private final BooleanProperty countOfSamples = new SimpleBooleanProperty();
    private final DataModelWidget widget;
    private final WidgetPojo config;
    private final ImageView imagePreview;


    public TimeFrameWidgetObject(DataModelWidget widget, WidgetPojo config, ImageView imagePreview) {
        this.widget = widget;
        this.config = config;
        this.imagePreview = imagePreview;

        addListener();
    }

    private void addListener() {

        countOfSamples.addListener((observableValue, aBoolean, t1) -> {
            if (t1) {
                startObjectProperty.set(Start.NONE);
                endObjectProperty.set(End.NONE);
            }
        });
        startObjectProperty.addListener((observableValue, start, t1) -> {
            if (!t1.equals(Start.NONE)) {
                countOfSamples.set(false);
            }
        });
        endObjectProperty.addListener((observableValue, start, t1) -> {
            if (!t1.equals(End.NONE)) {
                countOfSamples.set(false);
            }
        });
    }

    public ImageView getImagePreview() {
        return imagePreview;
    }

    public WidgetPojo getConfig() {
        return config;
    }

    public DataModelWidget getWidget() {
        return widget;
    }

    public List<DateTime> getMaxTimeStamps() {
        List<DateTime> dateTimes = new ArrayList<>();
        widget.getDataHandler().getMaxTimeStamps();
        return dateTimes;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public Start getStartObjectProperty() {
        return startObjectProperty.get();
    }

    public ObjectProperty<Start> startObjectPropertyProperty() {
        return startObjectProperty;
    }

    public void setStartObjectProperty(Start startObjectProperty) {
        this.startObjectProperty.set(startObjectProperty);
    }

    public End getEndObjectProperty() {
        return endObjectProperty.get();
    }

    public ObjectProperty<End> endObjectPropertyProperty() {
        return endObjectProperty;
    }

    public void setEndObjectProperty(End endObjectProperty) {
        this.endObjectProperty.set(endObjectProperty);
    }

    @Override
    public String toString() {
        return "TimeFrameWidgetObject{" +
                "selected=" + selected +
                ", startObjectProperty=" + startObjectProperty +
                ", endObjectProperty=" + endObjectProperty +
                ", config=" + config +
                '}';
    }

    public boolean getCountOfSamples() {
        return countOfSamples.get();
    }

    public void setCountOfSamples(boolean countOfSamples) {
        this.countOfSamples.set(countOfSamples);
    }

    public BooleanProperty countOfSamplesProperty() {
        return countOfSamples;
    }

    public enum Start {
        PERIODE_FROM, NONE

    }

    public enum End {
        LAST_TS, PERIODE_UNTIL, NONE

    }
}

