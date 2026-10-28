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
import javafx.beans.property.StringProperty;
import javafx.scene.control.TableColumn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisSample;
import org.jevis.api.JEVisType;
import org.jevis.commons.object.plugin.TargetHelper;
import org.jevis.jeconfig.plugin.meters.data.JEVisTypeWrapper;
import org.jevis.jeconfig.plugin.meters.data.MeterData;
import org.jevis.jeconfig.plugin.meters.event.PrecisionEventHandler;

import java.util.Optional;

public class LastRawValue extends TableColumn<MeterData, LastRawValuePojo> {

    private static final Logger logger = LogManager.getLogger(LastRawValue.class);

    private final JEVisTypeWrapper jeVisTypeWrapperDecimalPlaces;

    private final PrecisionEventHandler precisionEventHandler;

    public LastRawValue(String s, JEVisDataSource ds, JEVisType jeVisType, int width, JEVisTypeWrapper jeVisTypeWrapperDecimalPlaces, PrecisionEventHandler precisionEventHandler) {
        super(s);

        this.jeVisTypeWrapperDecimalPlaces = jeVisTypeWrapperDecimalPlaces;
        this.precisionEventHandler = precisionEventHandler;

        try {
            setId(jeVisType.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.setCellValueFactory(meterDataJEVisSampleCellDataFeatures -> {

            try {
                long precision = getPrecision(meterDataJEVisSampleCellDataFeatures.getValue());
                JEVisAttribute targetAttribute = meterDataJEVisSampleCellDataFeatures.getValue().getJeVisObject().getAttribute(jeVisType);
                TargetHelper th = new TargetHelper(ds, targetAttribute);
                double value = getValueAs(th).orElse(0.0);
                String unit = getUnit(th).orElse("");
                LastRawValuePojo lastRawValuePojo = new LastRawValuePojo(meterDataJEVisSampleCellDataFeatures.getValue(), value, unit, (int) precision, targetAttribute);

                return new SimpleObjectProperty<LastRawValuePojo>(lastRawValuePojo);
            } catch (Exception e) {
                logger.error(e);
            }

            return new SimpleObjectProperty<>();
        });

        this.setCellFactory(new LastRawValueCell<>(precisionEventHandler));
        this.setStyle("-fx-alignment: CENTER-RIGHT;");
        this.setMinWidth(width);
    }

    private static Optional<String> getUnit(TargetHelper th) {
        try {
            return Optional.of(th.getAttribute().get(0).getLatestSample().getUnit().getLabel());

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<Double> getValueAs(TargetHelper th) {
        try {
            return Optional.of(th.getAttribute().get(0).getLatestSample().getValueAsDouble());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void setStringProperty(StringProperty stringProperty, double value, long precision, String unitLabel) {
        stringProperty.set(String.format("%.0" + precision + "f", value) + " " + unitLabel);
    }

    private int getPrecision(MeterData meterData) {
        try {
            if (meterData.getJeVisAttributeJEVisSampleMap().get(jeVisTypeWrapperDecimalPlaces) == null) return 2;
            Optional<JEVisSample> optionalJEVisSample = (meterData.getJeVisAttributeJEVisSampleMap().get(jeVisTypeWrapperDecimalPlaces).getOptionalJEVisSample());
            if (!optionalJEVisSample.isPresent()) return 2;
            JEVisSample jeVisSample = optionalJEVisSample.get();
            return Math.toIntExact(jeVisSample.getValueAsLong());
        } catch (Exception e) {
            logger.error(e);
            return 2;
        }
    }
}
