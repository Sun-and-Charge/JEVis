/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.application.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.Icon;

/**
 * Form layout of the editors (docs/DESIGN-CC.md "Forms"): sections with a heading, labels above the fields, two
 * columns, wide rows for long texts, sections that start collapsed ("Weitere Angaben") and a file row.
 */
public class FormLayout extends VBox {

    public FormLayout() {
        super(22);
        getStyleClass().add("suc-form-layout");
        setPadding(new Insets(8, 20, 20, 20));
    }

    /** a section with a heading, always open */
    public Section section(String heading) {
        Section section = new Section();
        Label label = new Label(heading);
        label.getStyleClass().add("suc-form-section");
        VBox box = new VBox(10, label, section.grid);
        getChildren().add(box);
        return section;
    }

    /** a section that starts collapsed, for rarely used fields */
    public Section collapsed(String heading) {
        Section section = new Section();
        TitledPane pane = new TitledPane(heading, section.grid);
        pane.setExpanded(false);
        pane.setAnimated(false);
        pane.getStyleClass().add("suc-form-collapsed");
        getChildren().add(pane);
        return section;
    }

    /**
     * A file row: file name (or "Keine Datei"), download and upload.
     *
     * @param download null hides the download button
     */
    public static FileRow fileRow(String fileName, Runnable download, Runnable upload) {
        return new FileRow(fileName, download, upload);
    }

    public static final class FileRow extends HBox {
        private final Label name = new Label();
        private final Button download = new Button("", JEConfig.getSVGImage(Icon.EXPORT, 16, 16));

        private FileRow(String fileName, Runnable onDownload, Runnable onUpload) {
            super(6);
            setAlignment(Pos.CENTER_LEFT);
            getStyleClass().add("suc-file-row");
            name.getStyleClass().add("suc-file-name");
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            Button upload = new Button("", JEConfig.getSVGImage(Icon.CLOUD_UPLOAD, 16, 16));
            upload.setTooltip(new Tooltip(SucDialog.text("dialog.ui.file.upload", "Datei hochladen", "Upload file")));
            download.setTooltip(new Tooltip(SucDialog.text("dialog.ui.file.download", "Datei herunterladen", "Download file")));
            upload.setOnAction(event -> onUpload.run());
            if (onDownload != null) download.setOnAction(event -> onDownload.run());
            getChildren().addAll(name, download, upload);
            setFileName(fileName, onDownload != null);
        }

        public void setFileName(String fileName, boolean downloadable) {
            boolean has = fileName != null && !fileName.isEmpty();
            name.setText(has ? fileName : SucDialog.text("dialog.ui.file.none", "Keine Datei", "No file"));
            name.getStyleClass().remove("suc-form-empty");
            if (!has) name.getStyleClass().add("suc-form-empty");
            download.setVisible(has && downloadable);
            download.setManaged(download.isVisible());
        }
    }

    /** two columns of label-above-field cells */
    public static final class Section {
        private final GridPane grid = new GridPane();
        private int row;
        private int column;

        private Section() {
            grid.setHgap(16);
            grid.setVgap(12);
            ColumnConstraints half = new ColumnConstraints();
            half.setPercentWidth(50);
            half.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(half, half);
        }

        /** a field in the next free column */
        public Section add(String label, Node field) {
            grid.add(cell(label, field), column, row);
            column++;
            if (column > 1) {
                column = 0;
                row++;
            }
            return this;
        }

        /** a field over both columns, on its own row */
        public Section addWide(String label, Node field) {
            if (column != 0) {
                column = 0;
                row++;
            }
            grid.add(cell(label, field), 0, row, 2, 1);
            row++;
            return this;
        }

        public boolean isEmpty() {
            return grid.getChildren().isEmpty();
        }

        private static VBox cell(String label, Node field) {
            Label caption = new Label(label);
            caption.getStyleClass().add("suc-form-label");
            caption.setWrapText(true);
            if (field instanceof Control) ((Control) field).setMaxWidth(Double.MAX_VALUE);
            if (field instanceof Region) ((Region) field).setMaxWidth(Double.MAX_VALUE);
            VBox box = new VBox(4, caption, field);
            box.setFillWidth(true);
            return box;
        }
    }
}
