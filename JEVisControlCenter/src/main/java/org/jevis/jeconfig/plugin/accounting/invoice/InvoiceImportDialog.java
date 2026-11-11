/**
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
package org.jevis.jeconfig.plugin.accounting.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.relationship.ObjectRelations;
import org.jevis.jeapi.ws.InvoiceRequestException;
import org.jevis.jeapi.ws.JEVisDataSourceWS;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;

import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Import of ZUGFeRD / Factur-X and XRechnung files into an Energy Invoices Directory: choose the directory and the
 * files, parse them on the server, confirm medium and selection in a table, import and show a summary.
 * See docs/ENERGY-INVOICES.md.
 */
public class InvoiceImportDialog {

    public static final String DIRECTORY_CLASS = "Energy Invoices Directory";
    private static final String MEDIUM_ATTRIBUTE = "Medium";
    private static final Logger logger = LogManager.getLogger(InvoiceImportDialog.class);
    private static final String TASK_OWNER = InvoiceImportDialog.class.getName();

    private final JEVisDataSource ds;
    private final ObjectRelations objectRelations;
    private final NumberFormat amountFormat = NumberFormat.getNumberInstance(I18n.getInstance().getLocale());
    private final NumberFormat consumptionFormat = NumberFormat.getNumberInstance(I18n.getInstance().getLocale());
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(I18n.getInstance().getLocale());
    private final SimpleBooleanProperty importing = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty finished = new SimpleBooleanProperty(false);

    public InvoiceImportDialog(JEVisDataSource ds) {
        this.ds = ds;
        this.objectRelations = new ObjectRelations(ds);
        amountFormat.setMinimumFractionDigits(2);
        amountFormat.setMaximumFractionDigits(2);
        consumptionFormat.setMinimumFractionDigits(0);
        consumptionFormat.setMaximumFractionDigits(3);
    }

    private static String t(String key, Object... args) {
        return args.length == 0 ? I18n.getInstance().getString(key) : I18n.getInstance().getString(key, args);
    }

    /**
     * Energy Invoices Directories the current user may create objects in, without the medium sub-folders created
     * by the import.
     */
    public static List<JEVisObject> getImportDirectories(JEVisDataSource ds) {
        List<JEVisObject> result = new ArrayList<>();
        try {
            JEVisClass directoryClass = ds.getJEVisClass(DIRECTORY_CLASS);
            if (directoryClass == null) {
                return result;
            }
            JEVisUser user = ds.getCurrentUser();
            for (JEVisObject directory : ds.getObjects(directoryClass, false)) {
                if (directory.getDeleteTS() != null) continue;
                if (user != null && !user.canCreate(directory.getID())) continue;
                if (isMediumFolder(directory)) continue;
                result.add(directory);
            }
        } catch (Exception ex) {
            logger.error("Could not load the Energy Invoices Directories", ex);
        }
        return result;
    }

    private static boolean isMediumFolder(JEVisObject directory) {
        try {
            JEVisAttribute medium = directory.getAttribute(MEDIUM_ATTRIBUTE);
            if (medium == null || !medium.hasSample()) return false;
            JEVisSample latest = medium.getLatestSample();
            return latest != null && latest.getValueAsString() != null && !latest.getValueAsString().trim().isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Runs the whole import.
     *
     * @param context    selected object of the plugin (e.g. the accounting configuration) to preselect the
     *                   directory of its building, may be {@code null}
     * @param onImported called on the FX thread after at least one invoice was imported
     */
    public void show(JEVisObject context, Runnable onImported) {
        if (!(ds instanceof JEVisDataSourceWS)) {
            error(t("plugin.accounting.invoices.error.notsupported"));
            return;
        }
        List<JEVisObject> directories = getImportDirectories(ds);
        if (directories.isEmpty()) {
            error(t("plugin.accounting.invoices.nodirectory"));
            return;
        }
        JEVisObject directory = chooseDirectory(directories, context);
        if (directory == null) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(t("plugin.accounting.invoices.filechooser.title"));
        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(t("plugin.accounting.invoices.filechooser.filter"), "*.pdf", "*.xml", "*.PDF", "*.XML");
        fileChooser.getExtensionFilters().addAll(filter, new FileChooser.ExtensionFilter(t("plugin.accounting.invoices.filechooser.all"), "*.*"));
        fileChooser.setSelectedExtensionFilter(filter);
        File lastPath = JEConfig.getLastPath();
        if (lastPath != null && lastPath.isDirectory()) {
            fileChooser.setInitialDirectory(lastPath);
        }
        List<File> files = fileChooser.showOpenMultipleDialog(JEConfig.getStage());
        if (files == null || files.isEmpty()) {
            return;
        }
        JEConfig.setLastPath(files.get(0));

        parse(directory, new ArrayList<>(files), onImported);
    }

    private JEVisObject chooseDirectory(List<JEVisObject> directories, JEVisObject context) {
        if (directories.size() == 1) {
            return directories.get(0);
        }
        List<DirectoryChoice> choices = new ArrayList<>();
        DirectoryChoice preselected = null;
        JEVisObject contextBuilding = null;
        try {
            contextBuilding = context != null ? objectRelations.getBuildingParent(context) : null;
        } catch (Exception ex) {
            logger.debug("No building for {}", context, ex);
        }
        for (JEVisObject directory : directories) {
            DirectoryChoice choice = new DirectoryChoice(directory, objectRelations.getObjectPath(directory) + directory.getName());
            choices.add(choice);
            if (preselected == null && contextBuilding != null) {
                try {
                    JEVisObject building = objectRelations.getBuildingParent(directory);
                    if (building != null && building.getID().equals(contextBuilding.getID())) {
                        preselected = choice;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        choices.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        if (preselected == null) {
            preselected = choices.get(0);
        }
        ChoiceDialog<DirectoryChoice> dialog = new ChoiceDialog<>(preselected, choices);
        dialog.initOwner(JEConfig.getStage());
        dialog.setTitle(t("plugin.accounting.invoices.title"));
        dialog.setHeaderText(t("plugin.accounting.invoices.directory.header"));
        dialog.setContentText(t("plugin.accounting.invoices.directory.label"));
        return dialog.showAndWait().map(choice -> choice.directory).orElse(null);
    }

    private void parse(JEVisObject directory, List<File> files, Runnable onImported) {
        JEVisDataSourceWS ws = (JEVisDataSourceWS) ds;
        Task<List<InvoiceImportRow>> task = new Task<List<InvoiceImportRow>>() {
            @Override
            protected List<InvoiceImportRow> call() {
                updateTitle(t("plugin.accounting.invoices.parsing"));
                List<InvoiceImportRow> rows = new ArrayList<>();
                int i = 0;
                for (File file : files) {
                    updateMessage(file.getName());
                    updateProgress(i++, files.size());
                    rows.add(parseFile(ws, directory, file));
                }
                updateProgress(files.size(), files.size());
                return rows;
            }
        };
        task.setOnSucceeded(event -> showTable(directory, task.getValue(), onImported));
        task.setOnFailed(event -> {
            logger.error("Parsing invoices failed", task.getException());
            error(String.valueOf(task.getException() != null ? task.getException().getMessage() : ""));
        });
        JEConfig.getStatusBar().addTask(TASK_OWNER, task, JEConfig.getImage(Icon.PNG_ACCOUNTING), true);
    }

    private InvoiceImportRow parseFile(JEVisDataSourceWS ws, JEVisObject directory, File file) {
        try {
            JsonNode answer = ws.parseInvoice(file, directory.getID());
            return InvoiceImportRow.fromParseResult(file, answer);
        } catch (InvoiceRequestException ex) {
            logger.warn("Could not parse invoice {}: [{}] {}", file.getName(), ex.getHttpStatus(), ex.getMessage());
            return InvoiceImportRow.fromError(file, ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage());
        } catch (Exception ex) {
            logger.error("Could not parse invoice {}", file.getName(), ex);
            return InvoiceImportRow.fromError(file, 0, null, ex.getMessage() != null ? ex.getMessage() : ex.toString());
        }
    }

    private void showTable(JEVisObject directory, List<InvoiceImportRow> rows, Runnable onImported) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(JEConfig.getStage());
        dialog.setTitle(t("plugin.accounting.invoices.title"));
        dialog.setHeaderText(t("plugin.accounting.invoices.table.header", objectRelations.getObjectPath(directory) + directory.getName()));
        dialog.setResizable(true);

        TableView<InvoiceImportRow> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setEditable(true);
        table.setPrefSize(1250, 420);
        table.getColumns().addAll(buildColumns(table));

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(250);
        Label progressLabel = new Label();
        HBox progressBox = new HBox(8, progressBar, progressLabel);
        progressBox.setAlignment(Pos.CENTER_LEFT);
        progressBox.setVisible(false);
        progressBox.managedProperty().bind(progressBox.visibleProperty());

        VBox content = new VBox(8, table, progressBox);
        content.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);

        ButtonType importType = new ButtonType(t("plugin.accounting.invoices.button.import"), ButtonBar.ButtonData.OK_DONE);
        ButtonType closeType = new ButtonType(t("plugin.accounting.invoices.button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(importType, closeType);
        Button importButton = (Button) dialog.getDialogPane().lookupButton(importType);
        Button closeButton = (Button) dialog.getDialogPane().lookupButton(closeType);
        importButton.setDefaultButton(true);
        importButton.disableProperty().bind(importing.or(finished));
        closeButton.disableProperty().bind(importing);

        importButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            List<InvoiceImportRow> toImport = new ArrayList<>();
            for (InvoiceImportRow row : rows) {
                if (row.isSelected() && row.isImportable()) toImport.add(row);
            }
            if (toImport.isEmpty()) {
                return;
            }
            runImport(directory, rows, toImport, table, progressBox, progressBar, progressLabel, closeButton, onImported);
        });

        dialog.show();
    }

    private List<TableColumn<InvoiceImportRow, ?>> buildColumns(TableView<InvoiceImportRow> table) {
        List<TableColumn<InvoiceImportRow, ?>> columns = new ArrayList<>();

        TableColumn<InvoiceImportRow, Boolean> importColumn = new TableColumn<>(t("plugin.accounting.invoices.column.import"));
        importColumn.setCellValueFactory(param -> param.getValue().selectedProperty());
        importColumn.setCellFactory(param -> new SelectCell());
        importColumn.setPrefWidth(70);
        importColumn.setSortable(false);
        columns.add(importColumn);

        columns.add(textColumn(t("plugin.accounting.invoices.column.file"), 160, InvoiceImportRow::getFileName));
        columns.add(textColumn(t("plugin.accounting.invoices.column.format"), 140, InvoiceImportRow::getFormat));
        columns.add(textColumn(t("plugin.accounting.invoices.column.number"), 100, InvoiceImportRow::getNumber));
        columns.add(textColumn(t("plugin.accounting.invoices.column.date"), 90, row -> formatDate(row.getDate())));
        columns.add(textColumn(t("plugin.accounting.invoices.column.seller"), 150, InvoiceImportRow::getSeller));
        columns.add(textColumn(t("plugin.accounting.invoices.column.period"), 170, this::formatPeriod));
        columns.add(numberColumn(t("plugin.accounting.invoices.column.consumption"), 110, row -> row.getConsumption() == null ? null
                : consumptionFormat.format(row.getConsumption()) + (row.getConsumptionUnit() != null ? " " + row.getConsumptionUnit() : "")));
        columns.add(numberColumn(t("plugin.accounting.invoices.column.net"), 100, row -> formatAmount(row.getNet(), row.getCurrency())));
        columns.add(numberColumn(t("plugin.accounting.invoices.column.gross"), 100, row -> formatAmount(row.getGross(), row.getCurrency())));

        TableColumn<InvoiceImportRow, InvoiceMedium> mediumColumn = new TableColumn<>(t("plugin.accounting.invoices.column.medium"));
        mediumColumn.setCellValueFactory(param -> param.getValue().mediumProperty());
        mediumColumn.setCellFactory(param -> new MediumCell());
        mediumColumn.setPrefWidth(130);
        columns.add(mediumColumn);

        TableColumn<InvoiceImportRow, InvoiceImportRow.Status> statusColumn = new TableColumn<>(t("plugin.accounting.invoices.column.status"));
        statusColumn.setCellValueFactory(param -> param.getValue().statusProperty());
        statusColumn.setCellFactory(param -> new StatusCell());
        statusColumn.setPrefWidth(220);
        columns.add(statusColumn);

        return columns;
    }

    private TableColumn<InvoiceImportRow, String> textColumn(String title, double width, java.util.function.Function<InvoiceImportRow, String> value) {
        TableColumn<InvoiceImportRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(value.apply(param.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private TableColumn<InvoiceImportRow, String> numberColumn(String title, double width, java.util.function.Function<InvoiceImportRow, String> value) {
        TableColumn<InvoiceImportRow, String> column = textColumn(title, width, value);
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
        column.setSortable(false);
        return column;
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : dateFormat.format(date);
    }

    private String formatPeriod(InvoiceImportRow row) {
        if (row.getPeriodStart() == null && row.getPeriodEnd() == null) return "";
        return formatDate(row.getPeriodStart()) + " – " + formatDate(row.getPeriodEnd());
    }

    private String formatAmount(Double value, String currency) {
        if (value == null) return null;
        return amountFormat.format(value) + (currency != null ? " " + currency : "");
    }

    /**
     * Text of the status column.
     */
    String statusText(InvoiceImportRow row) {
        switch (row.getStatus()) {
            case NEW:
                return t("plugin.accounting.invoices.status.new");
            case DUPLICATE:
                return t("plugin.accounting.invoices.status.duplicate", String.valueOf(row.getDuplicateId()));
            case IMPORTED:
                return t("plugin.accounting.invoices.status.imported", String.valueOf(row.getImportedId()));
            case SKIPPED:
                return t("plugin.accounting.invoices.status.skipped", String.valueOf(row.getDuplicateId()));
            case ERROR:
            case FAILED:
            default:
                return t("plugin.accounting.invoices.status.error", errorText(row));
        }
    }

    private String errorText(InvoiceImportRow row) {
        if (row.getMessageKey() != null) {
            return t(row.getMessageKey());
        }
        return row.getMessage() != null ? row.getMessage() : "";
    }

    private void runImport(JEVisObject directory, List<InvoiceImportRow> allRows, List<InvoiceImportRow> toImport,
                           TableView<InvoiceImportRow> table, HBox progressBox, ProgressBar progressBar, Label progressLabel,
                           Button closeButton, Runnable onImported) {
        JEVisDataSourceWS ws = (JEVisDataSourceWS) ds;
        importing.set(true);
        table.refresh();
        progressBox.setVisible(true);

        Task<int[]> task = new Task<int[]>() {
            @Override
            protected int[] call() {
                updateTitle(t("plugin.accounting.invoices.importing"));
                int imported = 0, skipped = 0, failed = 0;
                int i = 0;
                for (InvoiceImportRow row : toImport) {
                    updateMessage(t("plugin.accounting.invoices.progress", i + 1, toImport.size(), row.getFileName()));
                    updateProgress(i++, toImport.size());
                    try {
                        JsonNode answer = ws.importInvoice(row.getFile(), directory.getID(), row.getMedium().getCode());
                        Long id = answer != null && answer.hasNonNull("invoiceId") ? answer.get("invoiceId").asLong() : null;
                        imported++;
                        Platform.runLater(() -> row.markImported(id));
                    } catch (InvoiceRequestException ex) {
                        if (ex.isDuplicate()) {
                            skipped++;
                            Long existing = ex.getExistingId();
                            Platform.runLater(() -> row.markDuplicate(existing));
                        } else {
                            failed++;
                            logger.warn("Import of {} failed: [{}] {}", row.getFileName(), ex.getHttpStatus(), ex.getMessage());
                            Platform.runLater(() -> row.markFailed(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage()));
                        }
                    } catch (Exception ex) {
                        failed++;
                        logger.error("Import of {} failed", row.getFileName(), ex);
                        String message = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                        Platform.runLater(() -> row.markFailed(0, null, message));
                    }
                }
                updateProgress(toImport.size(), toImport.size());
                return new int[]{imported, skipped, failed};
            }
        };
        progressBar.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());

        int parseErrors = (int) allRows.stream().filter(row -> row.getStatus() == InvoiceImportRow.Status.ERROR).count();
        int notSelected = allRows.size() - toImport.size() - parseErrors;
        task.setOnSucceeded(event -> finishImport(task.getValue(), notSelected, parseErrors, table, progressBar, progressLabel, closeButton, onImported));
        task.setOnFailed(event -> {
            logger.error("Invoice import failed", task.getException());
            finishImport(new int[]{0, 0, toImport.size()}, notSelected, parseErrors, table, progressBar, progressLabel, closeButton, onImported);
        });
        JEConfig.getStatusBar().addTask(TASK_OWNER, task, JEConfig.getImage(Icon.PNG_ACCOUNTING), true);
    }

    private void finishImport(int[] counts, int notSelected, int parseErrors, TableView<InvoiceImportRow> table, ProgressBar progressBar,
                              Label progressLabel, Button closeButton, Runnable onImported) {
        progressBar.progressProperty().unbind();
        progressLabel.textProperty().unbind();
        progressBar.setProgress(1);
        importing.set(false);
        finished.set(true);
        closeButton.setText(t("plugin.accounting.invoices.button.close"));
        table.refresh();

        String summary = t("plugin.accounting.invoices.summary", counts[0], counts[1] + notSelected, counts[2] + parseErrors);
        progressLabel.setText(summary);
        Alert alert = new Alert(counts[2] + parseErrors > 0 ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION, summary, ButtonType.OK);
        alert.initOwner(JEConfig.getStage());
        alert.setTitle(t("plugin.accounting.invoices.title"));
        alert.setHeaderText(t("plugin.accounting.invoices.summary.header"));
        alert.show();

        if (counts[0] > 0 && onImported != null) {
            try {
                onImported.run();
            } catch (Exception ex) {
                logger.error("Refresh after invoice import failed", ex);
            }
        }
    }

    private void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(JEConfig.getStage());
        alert.setTitle(t("plugin.accounting.invoices.title"));
        alert.setHeaderText(null);
        alert.show();
    }

    private static class DirectoryChoice {
        private final JEVisObject directory;
        private final String label;

        DirectoryChoice(JEVisObject directory, String label) {
            this.directory = directory;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Checkbox "import", disabled for files that could not be parsed, during and after the import.
     */
    private class SelectCell extends TableCell<InvoiceImportRow, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        SelectCell() {
            checkBox.setOnAction(event -> {
                InvoiceImportRow row = currentRow();
                if (row != null) row.setSelected(checkBox.isSelected());
            });
            setAlignment(Pos.CENTER);
        }

        private InvoiceImportRow currentRow() {
            int index = getIndex();
            return index >= 0 && index < getTableView().getItems().size() ? getTableView().getItems().get(index) : null;
        }

        @Override
        protected void updateItem(Boolean item, boolean empty) {
            super.updateItem(item, empty);
            InvoiceImportRow row = empty ? null : currentRow();
            if (row == null) {
                setGraphic(null);
                return;
            }
            checkBox.setSelected(Boolean.TRUE.equals(item));
            checkBox.setDisable(!row.isImportable() || importing.get() || finished.get());
            setGraphic(checkBox);
        }
    }

    /**
     * ComboBox with the medium, preselected from the answer of the server.
     */
    private class MediumCell extends TableCell<InvoiceImportRow, InvoiceMedium> {
        private final ComboBox<InvoiceMedium> comboBox = new ComboBox<>(FXCollections.observableArrayList(InvoiceMedium.values()));

        MediumCell() {
            comboBox.setConverter(new StringConverter<InvoiceMedium>() {
                @Override
                public String toString(InvoiceMedium medium) {
                    return medium == null ? "" : medium.getDisplayName();
                }

                @Override
                public InvoiceMedium fromString(String string) {
                    for (InvoiceMedium medium : InvoiceMedium.values()) {
                        if (medium.getDisplayName().equals(string)) return medium;
                    }
                    return InvoiceMedium.OTHER;
                }
            });
            comboBox.setMaxWidth(Double.MAX_VALUE);
            comboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
                InvoiceImportRow row = currentRow();
                if (row != null && newValue != null && !Objects.equals(row.getMedium(), newValue)) {
                    row.setMedium(newValue);
                }
            });
        }

        private InvoiceImportRow currentRow() {
            int index = getIndex();
            return index >= 0 && index < getTableView().getItems().size() ? getTableView().getItems().get(index) : null;
        }

        @Override
        protected void updateItem(InvoiceMedium item, boolean empty) {
            super.updateItem(item, empty);
            InvoiceImportRow row = empty ? null : currentRow();
            if (row == null || !row.isImportable() && row.getStatus() == InvoiceImportRow.Status.ERROR) {
                setGraphic(null);
                setText(null);
                return;
            }
            comboBox.setValue(item);
            comboBox.setDisable(!row.isImportable() || importing.get() || finished.get());
            setText(null);
            setGraphic(comboBox);
        }
    }

    /**
     * Status text with the warnings and the reasons for the medium as tooltip.
     */
    private class StatusCell extends TableCell<InvoiceImportRow, InvoiceImportRow.Status> {
        @Override
        protected void updateItem(InvoiceImportRow.Status item, boolean empty) {
            super.updateItem(item, empty);
            int index = getIndex();
            if (empty || item == null || index < 0 || index >= getTableView().getItems().size()) {
                setText(null);
                setTooltip(null);
                setStyle("");
                return;
            }
            InvoiceImportRow row = getTableView().getItems().get(index);
            String text = statusText(row);
            setText(text);

            StringBuilder tip = new StringBuilder(text);
            if (!row.getMediumReasons().isEmpty()) {
                tip.append("\n").append(t("plugin.accounting.invoices.mediumreasons")).append(" ").append(String.join(", ", row.getMediumReasons()));
            }
            for (String warning : row.getWarnings()) {
                tip.append("\n").append(warning);
            }
            setTooltip(new Tooltip(tip.toString()));

            switch (item) {
                case ERROR:
                case FAILED:
                    setStyle("-fx-text-fill: #c62828;");
                    break;
                case DUPLICATE:
                case SKIPPED:
                    setStyle("-fx-text-fill: #9a6700;");
                    break;
                case IMPORTED:
                    setStyle("-fx-text-fill: #2e7d32;");
                    break;
                default:
                    setStyle(row.getWarnings().isEmpty() ? "" : "-fx-text-fill: #9a6700;");
            }
        }
    }
}
