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
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisUnit;
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.jeapi.ws.JEVisObjectWS;
import org.joda.time.DateTime;
import tech.units.indriya.AbstractUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TableSample {
    final private DateTime timeStamp;
    private final List<String> columnValues = new ArrayList<>();
    private final List<Double> columnNumbers = new ArrayList<>();
    private final List<Long> columnNumbersSize = new ArrayList<>();
    private final List<XYChartSerie> chartSeries = new ArrayList<>();
    private final List<JEVisUnit> units = new ArrayList<>();
    private final List<NumberFormat> numberFormats = new ArrayList<>();
    private final List<Boolean> isCalculation = new ArrayList<>();
    private final List<JEVisObject> calculationObjects = new ArrayList<>();
    private final UUID uuid;
    private String columnIdentifier = "";

    public TableSample(DateTime timeStamp, int columns) {
        this.timeStamp = timeStamp;
        this.uuid = UUID.randomUUID();

        for (int i = 0; i < columns; i++) {
            columnValues.add("");
            columnNumbers.add(0d);
            columnNumbersSize.add(0L);
            numberFormats.add(NumberFormat.getInstance());
            isCalculation.add(false);
            calculationObjects.add(new JEVisObjectWS(null, new JsonObject()));
            chartSeries.add(new XYChartSerie());

            units.add(new JEVisUnitImp(AbstractUnit.ONE));
        }
    }

    public DateTime getTimeStamp() {
        return timeStamp;
    }

    public List<String> getColumnValues() {
        return columnValues;
    }

    public List<Double> getColumnNumbers() {
        return columnNumbers;
    }

    public List<Long> getColumnNumbersSize() {
        return columnNumbersSize;
    }

    public List<NumberFormat> getNumberFormats() {
        return numberFormats;
    }

    public List<Boolean> isCalculation() {
        return isCalculation;
    }

    public List<JEVisObject> getCalculationObjects() {
        return calculationObjects;
    }

    public List<JEVisUnit> getUnits() {
        return units;
    }

    public String getColumnIdentifier() {
        return columnIdentifier;
    }

    public void setColumnIdentifier(String columnIdentifier) {
        this.columnIdentifier = columnIdentifier;
    }

    public UUID getUuid() {
        return uuid;
    }

    public List<XYChartSerie> getChartSeries() {
        return chartSeries;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TableSample) {
            TableSample otherObj = (TableSample) obj;
            return otherObj.getUuid().equals(this.getUuid()) && otherObj.getTimeStamp().equals(this.getTimeStamp());
        }
        return false;
    }
}
