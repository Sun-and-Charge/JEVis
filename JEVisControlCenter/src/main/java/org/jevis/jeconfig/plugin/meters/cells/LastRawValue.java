/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
import org.jevis.commons.object.ValueFormat;

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
                JEVisAttribute targetAttribute = meterDataJEVisSampleCellDataFeatures.getValue().getJeVisObject().getAttribute(jeVisType);
                TargetHelper th = new TargetHelper(ds, targetAttribute);
                long precision = getPrecision(meterDataJEVisSampleCellDataFeatures.getValue(), th);
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
        stringProperty.set(ValueFormat.numberFormat((int) precision).format(value) + " " + unitLabel);
    }

    /**
     * The decimal places set on the meter in this plugin, otherwise those of the target data row
     * ({@link ValueFormat}, upstream issue #1194).
     */
    private int getPrecision(MeterData meterData, TargetHelper th) {
        try {
            if (meterData.getJeVisAttributeJEVisSampleMap().get(jeVisTypeWrapperDecimalPlaces) != null) {
                Optional<JEVisSample> optionalJEVisSample = (meterData.getJeVisAttributeJEVisSampleMap().get(jeVisTypeWrapperDecimalPlaces).getOptionalJEVisSample());
                if (optionalJEVisSample.isPresent()) {
                    Integer decimals = ValueFormat.parse(optionalJEVisSample.get().getValueAsString());
                    if (decimals != null) {
                        return decimals;
                    }
                }
            }
            if (th != null && th.isValid() && th.targetObjectAccessible() && !th.getObject().isEmpty()) {
                return ValueFormat.decimals(th.getObject().get(0));
            }
        } catch (Exception e) {
            logger.error(e);
        }
        return ValueFormat.DEFAULT_DECIMALS;
    }
}
