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
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import org.jevis.api.JEVisDataSource;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.plugin.meters.data.MeterData;
import org.jevis.jeconfig.plugin.meters.qr.MeterQrDialog;

/**
 * Column with a button per meter that opens the meter's QR code.
 */
public class QrColumn extends TableColumn<MeterData, MeterData> {

    public QrColumn(String name, int width, JEVisDataSource ds) {
        super(name);
        setId("QR");
        setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        setCellFactory(column -> new TableCell<MeterData, MeterData>() {
            private final Button button = new Button("", JEConfig.getSVGImage(Icon.QR_CODE, 18, 18));

            {
                button.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.meters.qr.title")));
                button.setOnAction(event -> {
                    if (getItem() != null) new MeterQrDialog(getItem(), ds).show();
                });
            }

            @Override
            protected void updateItem(MeterData item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(item == null || empty ? null : button);
            }
        });
        setStyle("-fx-alignment: CENTER;");
        setSortable(false);
        setMinWidth(width);
    }
}
