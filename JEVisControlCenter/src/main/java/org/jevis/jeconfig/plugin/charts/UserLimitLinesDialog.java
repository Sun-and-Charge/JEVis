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
package org.jevis.jeconfig.plugin.charts;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.application.Chart.data.UserLimitLine;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dialog to add and remove user defined horizontal limit lines of an analysis
 * (upstream issues #2233 and #1036).
 */
public class UserLimitLinesDialog {

    private static final String INVALID_STYLE = "-fx-border-color: red;";

    private final List<RowEditor> rows = new ArrayList<>();
    private final GridPane gridPane = new GridPane();

    /**
     * Shows the dialog modal and returns the edited limit lines.
     *
     * @param limitLines the current limit lines, not modified by this dialog
     * @return the new limit lines if the user confirmed the dialog, otherwise empty
     */
    public Optional<List<UserLimitLine>> show(List<UserLimitLine> limitLines) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.getInstance().getString("plugin.graph.userlimits.title"));
        dialog.setHeaderText(I18n.getInstance().getString("plugin.graph.userlimits.title"));
        dialog.setResizable(true);
        dialog.initOwner(JEConfig.getStage());
        dialog.initModality(Modality.APPLICATION_MODAL);
        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        TopMenu.applyActiveTheme(stage.getScene());

        if (limitLines != null) {
            for (UserLimitLine limitLine : limitLines) {
                rows.add(new RowEditor(new UserLimitLine(limitLine)));
            }
        }

        gridPane.setHgap(6);
        gridPane.setVgap(6);
        gridPane.setPadding(new Insets(4));
        rebuildGrid();

        Button addButton = new Button(I18n.getInstance().getString("plugin.graph.userlimits.add"));
        addButton.setOnAction(event -> {
            rows.add(new RowEditor(new UserLimitLine()));
            rebuildGrid();
        });

        ScrollPane scrollPane = new ScrollPane(gridPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(220);
        scrollPane.setPrefViewportWidth(760);

        VBox content = new VBox(8, scrollPane, addButton);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);

        ButtonType okType = new ButtonType(I18n.getInstance().getString("graph.dialog.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(I18n.getInstance().getString("graph.dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, okType);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(okType);
        okButton.setDefaultButton(true);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            boolean valid = true;
            for (RowEditor row : rows) {
                valid &= row.validate();
            }
            if (!valid) {
                event.consume();
            }
        });

        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(cancelType);
        cancelButton.setCancelButton(true);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == okType) {
            List<UserLimitLine> newLines = new ArrayList<>();
            for (RowEditor row : rows) {
                newLines.add(row.toLimitLine());
            }
            return Optional.of(newLines);
        }
        return Optional.empty();
    }

    private void rebuildGrid() {
        gridPane.getChildren().clear();

        gridPane.addRow(0,
                new Label(I18n.getInstance().getString("plugin.graph.userlimits.value")),
                new Label(I18n.getInstance().getString("plugin.graph.userlimits.label")),
                new Label(I18n.getInstance().getString("plugin.graph.userlimits.color")),
                new Label(I18n.getInstance().getString("plugin.graph.userlimits.axis")),
                new Label(I18n.getInstance().getString("plugin.graph.userlimits.fill")));

        int rowIndex = 1;
        for (RowEditor row : rows) {
            Button removeButton = new Button(I18n.getInstance().getString("plugin.graph.userlimits.remove"));
            removeButton.setOnAction(event -> {
                rows.remove(row);
                rebuildGrid();
            });
            gridPane.addRow(rowIndex, row.valueField, row.labelField, row.colorPicker, row.axisBox, row.fillBox, removeButton);
            rowIndex++;
        }
    }

    static Double parseValue(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim().replace(" ", "");
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(trimmed.replace(',', '.'));
        } catch (NumberFormatException ignored) {
        }
        try {
            NumberFormat numberFormat = NumberFormat.getInstance(I18n.getInstance().getLocale());
            ParsePosition position = new ParsePosition(0);
            Number number = numberFormat.parse(trimmed, position);
            if (number != null && position.getIndex() == trimmed.length()) {
                return number.doubleValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String formatValue(double value) {
        try {
            return new BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return Double.toString(value);
        }
    }

    private static class RowEditor {
        private final TextField valueField = new TextField();
        private final TextField labelField = new TextField();
        private final ColorPicker colorPicker = new ColorPicker();
        private final ComboBox<Integer> axisBox = new ComboBox<>();
        private final ComboBox<String> fillBox = new ComboBox<>();

        RowEditor(UserLimitLine limitLine) {
            valueField.setText(formatValue(limitLine.getValue()));
            valueField.setPrefWidth(110);
            valueField.textProperty().addListener((observable, oldValue, newValue) -> validate());

            labelField.setText(limitLine.getLabel() != null ? limitLine.getLabel() : "");
            labelField.setPrefWidth(180);

            colorPicker.setValue(limitLine.getFxColor());
            colorPicker.setPrefWidth(110);

            axisBox.getItems().addAll(0, 1);
            axisBox.setConverter(new StringConverter<Integer>() {
                @Override
                public String toString(Integer axis) {
                    if (axis != null && axis == 1) {
                        return I18n.getInstance().getString("plugin.graph.userlimits.axis.right");
                    }
                    return I18n.getInstance().getString("plugin.graph.userlimits.axis.left");
                }

                @Override
                public Integer fromString(String string) {
                    return null;
                }
            });
            axisBox.getSelectionModel().select(limitLine.getAxis() == 1 ? Integer.valueOf(1) : Integer.valueOf(0));

            fillBox.getItems().addAll(UserLimitLine.FILL_NONE, UserLimitLine.FILL_ABOVE, UserLimitLine.FILL_BELOW);
            fillBox.setConverter(new StringConverter<String>() {
                @Override
                public String toString(String fill) {
                    if (UserLimitLine.FILL_ABOVE.equals(fill)) {
                        return I18n.getInstance().getString("plugin.graph.userlimits.fill.above");
                    } else if (UserLimitLine.FILL_BELOW.equals(fill)) {
                        return I18n.getInstance().getString("plugin.graph.userlimits.fill.below");
                    }
                    return I18n.getInstance().getString("plugin.graph.userlimits.fill.none");
                }

                @Override
                public String fromString(String string) {
                    return null;
                }
            });
            String fill = limitLine.getFill();
            if (!fillBox.getItems().contains(fill)) {
                fill = UserLimitLine.FILL_NONE;
            }
            fillBox.getSelectionModel().select(fill);
        }

        boolean validate() {
            Double value = parseValue(valueField.getText());
            boolean valid = value != null && !value.isNaN() && !value.isInfinite();
            valueField.setStyle(valid ? "" : INVALID_STYLE);
            return valid;
        }

        UserLimitLine toLimitLine() {
            UserLimitLine limitLine = new UserLimitLine();
            Double value = parseValue(valueField.getText());
            limitLine.setValue(value != null ? value : 0d);
            limitLine.setLabel(labelField.getText() != null ? labelField.getText().trim() : "");
            limitLine.setFxColor(colorPicker.getValue());
            Integer axis = axisBox.getSelectionModel().getSelectedItem();
            limitLine.setAxis(axis != null ? axis : 0);
            String fill = fillBox.getSelectionModel().getSelectedItem();
            limitLine.setFill(fill != null ? fill : UserLimitLine.FILL_NONE);
            return limitLine;
        }
    }
}
