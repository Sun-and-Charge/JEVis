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
package org.jevis.jeconfig.application.Chart.ChartElements;

import com.ibm.icu.text.NumberFormat;
import javafx.beans.property.*;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Data model for a single row in a {@link org.jevis.jeconfig.application.Chart.Charts.TableChartH}.
 * <p>
 * Each {@code TableHSample} holds one data-series entry consisting of the series name,
 * the (latest) numeric value, the measurement unit, and an optional series colour used
 * for colour-coded value cells.
 * <p>
 * The special "sum" row is created via the {@link #TableHSample(double, String, String)}
 * constructor; ordinary series rows use {@link #TableHSample(XYChartSerie)}.
 *
 * @see org.jevis.jeconfig.application.Chart.Charts.TableCells.ValueTableCell
 */
public class TableHSample {
    private static final Logger logger = LogManager.getLogger(TableHSample.class);
    private final NumberFormat nf;
    private final StringProperty sampleName = new SimpleStringProperty(this, "sampleName", "");
    private final DoubleProperty sampleValue = new SimpleDoubleProperty(this, "sampleValue", 0);
    private final StringProperty sampleUnit = new SimpleStringProperty(this, "sampleUnit", "");
    private final ObjectProperty<Color> sampleColor = new SimpleObjectProperty<>(this, "sampleColor", null);


    /**
     * Used for the sum row only
     */
    public TableHSample(double value, String name, String unit) {
        setSampleName(name);
        setSampleValue(value);
        setSampleUnit(unit);
        nf = com.ibm.icu.text.NumberFormat.getNumberInstance();
    }

    public TableHSample(XYChartSerie xyChartSerie) {
        setSampleName(xyChartSerie.getTableEntryName());
        try {
            setSampleValue(xyChartSerie.getSingleRow().getSamples().get(0).getValueAsDouble());
        } catch (Exception e) {
            logger.error("Failed to read sample value for series '{}'", xyChartSerie.getTableEntryName(), e);
        }
        setSampleUnit(xyChartSerie.getUnit());
        nf = xyChartSerie.getNf();
    }

    public String getSampleName() {
        return sampleName.get();
    }

    public void setSampleName(String sampleName) {
        this.sampleName.set(sampleName);
    }

    public StringProperty sampleNameProperty() {
        return sampleName;
    }

    public double getSampleValue() {
        return sampleValue.get();
    }

    public void setSampleValue(double sampleValue) {
        this.sampleValue.set(sampleValue);
    }

    public DoubleProperty sampleValueProperty() {
        return sampleValue;
    }

    public String getSampleUnit() {
        return sampleUnit.get();
    }

    public void setSampleUnit(String sampleUnit) {
        this.sampleUnit.set(sampleUnit);
    }

    public StringProperty sampleUnitProperty() {
        return sampleUnit;
    }

    public Color getSampleColor() {
        return sampleColor.get();
    }

    public void setSampleColor(Color sampleColor) {
        this.sampleColor.set(sampleColor);
    }

    public ObjectProperty<Color> sampleColorProperty() {
        return sampleColor;
    }

    public NumberFormat getNf() {
        return nf;
    }
}
