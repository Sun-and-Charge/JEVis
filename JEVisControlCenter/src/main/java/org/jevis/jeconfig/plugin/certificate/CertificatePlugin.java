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
package org.jevis.jeconfig.plugin.certificate;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.JEVisFileImp;
import org.jevis.commons.certificate.CertificateExport;
import org.jevis.commons.certificate.CertificateLogic;
import org.jevis.commons.certificate.CertificateState;
import org.jevis.commons.certificate.EnergyCertificate;
import org.jevis.commons.certificate.TransferSchedule;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.Plugin;
import org.jevis.jeconfig.application.table.TableColumnStateStore;
import org.jevis.jeconfig.application.ui.Confirm;
import org.jevis.jeconfig.application.ui.FormLayout;
import org.jevis.jeconfig.application.ui.Notify;
import org.jevis.jeconfig.application.ui.SidePanel;
import org.jevis.jeconfig.plugin.appointment.AbstractEntryPlugin;
import org.jevis.jeconfig.plugin.appointment.AppointmentPlugin;
import org.jevis.jeconfig.plugin.appointment.EntryStore;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

import static org.jevis.jeconfig.application.navigation.PluginSidebar.text;

/**
 * Module "Energieausweise" (tender point BA-07, docs/ENERGY-CERTIFICATES.md): existing energy certificates of the
 * buildings with values, validity, responsible persons and PDF; one overview with status filter and CSV export; tab
 * "Übermittlung" with the transfer targets and "Jetzt übermitteln". Reminders and transfers are done by JENotifier.
 */
public class CertificatePlugin extends AbstractEntryPlugin {

    public static final String PLUGIN_NAME = "Energy Certificate Plugin";
    static final String CLASS_TRANSFER = "Energy Certificate Transfer";
    private static final Logger logger = LogManager.getLogger(CertificatePlugin.class);

    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final FilteredList<Row> filteredRows = new FilteredList<>(rows);
    private final TableView<Row> table = new TableView<>();
    private final ObservableList<TransferRow> transfers = FXCollections.observableArrayList();
    private final TableView<TransferRow> transferTable = new TableView<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ToolBar toolBar = new ToolBar();
    private final EntryStore.NameCache names;

    static class Row {
        final JEVisObject object;
        final EnergyCertificate certificate;
        final CertificateState state;
        final String building;
        final String fileName;
        final JEVisSample file;

        Row(JEVisObject object, EnergyCertificate certificate, CertificateState state, String building, JEVisSample file) {
            this.object = object;
            this.certificate = certificate;
            this.state = state;
            this.building = building;
            this.file = file;
            String name = null;
            try {
                name = file != null ? file.getValueAsString() : null;
            } catch (Exception e) {
                logger.debug("No file name", e);
            }
            this.fileName = name;
        }

        CertificateLogic.Status status() {
            return CertificateLogic.status(certificate, new LocalDate());
        }
    }

    static class TransferRow {
        final JEVisObject object;
        final Map<String, String> values;
        final TransferSchedule.LastTransfer last;

        TransferRow(JEVisObject object, Map<String, String> values) {
            this.object = object;
            this.values = values;
            this.last = TransferSchedule.LastTransfer.fromJson(values.get("Last Transfer"));
        }
    }

    public CertificatePlugin(JEVisDataSource ds, String name) {
        super(ds, name, "EnergyCertificates");
        this.names = new EntryStore.NameCache(ds);
        buildToolbar();
        buildContent();
    }

    static String typeText(EnergyCertificate.Type type) {
        return type == EnergyCertificate.Type.DEMAND ? text("plugin.certificates.type.demand", "Bedarfsausweis", "Demand certificate")
                : text("plugin.certificates.type.consumption", "Verbrauchsausweis", "Consumption certificate");
    }

    static String statusText(CertificateLogic.Status status) {
        switch (status) {
            case EXPIRED:
                return text("plugin.certificates.status.expired", "Abgelaufen", "Expired");
            case EXPIRING:
                return text("plugin.certificates.status.expiring", "Läuft ab", "Expiring");
            case VALID:
                return text("plugin.certificates.status.valid", "Gültig", "Valid");
            default:
                return text("plugin.certificates.status.unknown", "Ohne Datum", "No date");
        }
    }

    private static String date(String iso) {
        LocalDate d = CertificateLogic.date(iso);
        return d != null ? d.toString("dd.MM.yyyy") : "";
    }

    private void buildToolbar() {
        ToggleButton reload = AppointmentPlugin.toolbarButton(Icon.REFRESH, "plugin.scheduledentries.reload");
        ToggleButton add = AppointmentPlugin.toolbarButton(Icon.PLUS, "plugin.scheduledentries.reload");
        add.setTooltip(new Tooltip(text("plugin.certificates.new", "Neuer Energieausweis", "New energy certificate")));
        ToggleButton edit = AppointmentPlugin.toolbarButton(Icon.EDIT, "plugin.scheduledentries.reload");
        edit.setTooltip(new Tooltip(text("plugin.certificates.edit", "Energieausweis bearbeiten", "Edit energy certificate")));
        ToggleButton delete = AppointmentPlugin.toolbarButton(Icon.DELETE, "plugin.scheduledentries.reload");
        delete.setTooltip(new Tooltip(text("plugin.certificates.delete", "Energieausweis löschen", "Delete energy certificate")));
        ToggleButton export = AppointmentPlugin.toolbarButton(Icon.EXPORT, "plugin.scheduledentries.reload");
        export.setTooltip(new Tooltip(text("plugin.certificates.export", "Übersicht als CSV exportieren", "Export the overview as CSV")));
        reload.setOnAction(event -> reload());
        add.setOnAction(event -> create());
        edit.setOnAction(event -> editSelected());
        delete.setOnAction(event -> deleteSelected());
        export.setOnAction(event -> exportCsv());
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        toolBar.getItems().setAll(reload, new Separator(), add, edit, delete, new Separator(), export);
    }

    private void buildContent() {
        TableColumn<Row, String> building = column("building", text("plugin.certificates.building", "Gebäude", "Building"), 200, r -> r.building);
        TableColumn<Row, String> type = column("type", text("plugin.certificates.col.type", "Art", "Type"), 150, r -> r.certificate.getType() != null ? typeText(r.certificate.getType()) : "");
        TableColumn<Row, String> reg = column("registration", text("plugin.certificates.registration", "Registriernummer", "Registration number"), 160, r -> nz(r.certificate.getRegistrationNumber()));
        TableColumn<Row, String> issued = column("issued", text("plugin.certificates.col.issued", "Ausgestellt", "Issued"), 100, r -> date(r.certificate.getIssued()));
        TableColumn<Row, String> until = column("validuntil", text("plugin.certificates.col.validuntil", "Gültig bis", "Valid until"), 100, r -> r.certificate.getValidUntil() != null ? r.certificate.getValidUntil() : "");
        until.setCellFactory(param -> new TableCell<Row, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : date(item));
            }
        });
        TableColumn<Row, String> status = column("status", text("plugin.certificates.col.status", "Status", "Status"), 150, r -> {
            String s = statusText(r.status());
            LocalDate u = CertificateLogic.date(r.certificate.getValidUntil());
            if (u == null) return s;
            int days = Days.daysBetween(new LocalDate(), u).getDays();
            return s + (days >= 0 ? " (" + String.format(text("plugin.certificates.daysleft", "noch %d Tage", "%d days left"), days) + ")" : "");
        });
        TableColumn<Row, String> cls = column("class", text("plugin.certificates.col.class", "Klasse", "Class"), 60, r -> nz(r.certificate.getEnergyClass()));
        TableColumn<Row, String> value = column("value", text("plugin.certificates.col.value", "Kennwert kWh/(m²·a)", "Value kWh/(m²·a)"), 130, r -> {
            Double v = r.certificate.getType() == EnergyCertificate.Type.CONSUMPTION && r.certificate.getHeatConsumption() != null
                    ? r.certificate.getHeatConsumption() : r.certificate.getFinalEnergy();
            return CertificateForm.number(v);
        });
        TableColumn<Row, String> issuer = column("issuer", text("plugin.certificates.issuer", "Aussteller", "Issuer"), 160, r -> nz(r.certificate.getIssuer()));
        TableColumn<Row, String> responsible = column("responsible", text("plugin.certificates.col.responsible", "Zuständig", "Responsible"), 160, r -> names.names(r.certificate.getResponsible()));
        TableColumn<Row, String> pdf = column("pdf", "PDF", 60, r -> r.fileName != null ? "✓" : "");
        table.getColumns().addAll(building, type, reg, issued, until, status, cls, value, issuer, responsible, pdf);
        SortedList<Row> sorted = new SortedList<>(filteredRows);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        until.setSortType(TableColumn.SortType.ASCENDING);
        table.getSortOrder().add(until);
        table.setPlaceholder(new Label(text("plugin.certificates.empty", "Keine Energieausweise für diese Auswahl.", "No energy certificates for this selection.")));
        TableColumnStateStore.bind(table, "certificates");
        table.setRowFactory(param -> {
            TableRow<Row> row = new TableRow<Row>() {
                @Override
                protected void updateItem(Row item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) setStyle("");
                    else if (item.status() == CertificateLogic.Status.EXPIRED) setStyle("-fx-text-background-color: #c62828;");
                    else if (item.status() == CertificateLogic.Status.EXPIRING) setStyle("-fx-text-background-color: #8a6100;");
                    else setStyle("");
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) editSelected();
            });
            return row;
        });

        statusFilter.getItems().addAll(
                text("plugin.certificates.filter.all", "Alle", "All"),
                text("plugin.certificates.filter.attention", "Handlungsbedarf", "Action needed"),
                statusText(CertificateLogic.Status.EXPIRED), statusText(CertificateLogic.Status.EXPIRING),
                statusText(CertificateLogic.Status.VALID));
        statusFilter.getSelectionModel().selectFirst();
        searchField.setPromptText(text("plugin.certificates.search", "Gebäude, Nummer, Aussteller …", "Building, number, issuer …"));
        searchField.textProperty().addListener((o, a, b) -> updateFilter());
        statusFilter.valueProperty().addListener((o, a, b) -> updateFilter());
        HBox filterBar = new HBox(8, new Label(text("plugin.certificates.filter", "Status", "Status")), statusFilter, searchField);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(8));
        Label hint = new Label(text("plugin.certificates.hint",
                "Vorhandene Energieausweise der Gebäude. Erinnerungen vor Ablauf und Übermittlungen an Dritte versendet der Dienst JENotifier.",
                "Existing energy certificates of the buildings. Reminders before expiry and transfers to third parties are sent by the JENotifier service."));
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        hint.getStyleClass().add("suc-form-empty");
        VBox listPane = new VBox(filterBar, table, hint);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        VBox.setMargin(hint, new Insets(6, 8, 6, 8));

        TableColumn<TransferRow, String> tName = new TableColumn<>(text("plugin.certificates.transfer.name", "Ziel", "Target"));
        tName.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().object.getName()));
        tName.setPrefWidth(220);
        TableColumn<TransferRow, String> tChannel = new TableColumn<>(text("plugin.certificates.transfer.channel", "Kanal", "Channel"));
        tChannel.setCellValueFactory(p -> new ReadOnlyStringWrapper(nz(p.getValue().values.get("Channel"))));
        TableColumn<TransferRow, String> tFormat = new TableColumn<>(text("plugin.certificates.transfer.format", "Format", "Format"));
        tFormat.setCellValueFactory(p -> new ReadOnlyStringWrapper(nz(p.getValue().values.get("Format"))));
        TableColumn<TransferRow, String> tSchedule = new TableColumn<>(text("plugin.certificates.transfer.schedule", "Zeitplan", "Schedule"));
        tSchedule.setCellValueFactory(p -> new ReadOnlyStringWrapper(scheduleText(p.getValue().values.get("Schedule"))));
        TableColumn<TransferRow, String> tEnabled = new TableColumn<>(text("plugin.certificates.transfer.enabled", "Aktiv", "Enabled"));
        tEnabled.setCellValueFactory(p -> new ReadOnlyStringWrapper("true".equalsIgnoreCase(p.getValue().values.get("Enabled")) ? "✓" : ""));
        TableColumn<TransferRow, String> tLast = new TableColumn<>(text("plugin.certificates.transfer.last", "Letzte Übermittlung", "Last transfer"));
        tLast.setCellValueFactory(p -> new ReadOnlyStringWrapper(lastText(p.getValue().last)));
        tLast.setPrefWidth(280);
        TableColumn<TransferRow, String> tRequested = new TableColumn<>(text("plugin.certificates.transfer.requested", "Angefordert", "Requested"));
        tRequested.setCellValueFactory(p -> new ReadOnlyStringWrapper(nz(p.getValue().values.get("Requested")).isEmpty() ? "" : "✓"));
        transferTable.getColumns().addAll(tName, tEnabled, tChannel, tFormat, tSchedule, tLast, tRequested);
        transferTable.setItems(transfers);
        transferTable.setPlaceholder(new Label(text("plugin.certificates.transfer.empty",
                "Keine Übermittlungsziele. Legen Sie unter Konfiguration ein Objekt „Übermittlung Energieausweise“ beim Gebäude oder der Organisation an.",
                "No transfer targets. Create an object \"Energy Certificate Transfer\" at the building or organisation in Configuration.")));
        Button run = new Button(text("plugin.certificates.transfer.run", "Jetzt übermitteln", "Transfer now"), JEConfig.getSVGImage(Icon.MAIL, 16, 16));
        run.disableProperty().bind(transferTable.getSelectionModel().selectedItemProperty().isNull());
        run.setOnAction(event -> requestTransfer());
        Label transferHint = new Label(text("plugin.certificates.transfer.hint",
                "Der Dienst JENotifier übermittelt nach Zeitplan oder auf Anforderung mit seinem nächsten Lauf. Kanal, Format, Zeitplan, Empfänger und Zugangsdaten werden unter Konfiguration am Objekt gepflegt.",
                "The JENotifier service sends on schedule or on request with its next run. Channel, format, schedule, recipients and credentials are maintained on the object in Configuration."));
        transferHint.setWrapText(true);
        transferHint.getStyleClass().add("suc-form-empty");
        VBox transferPane = new VBox(8, new HBox(8, run), transferTable, transferHint);
        transferPane.setPadding(new Insets(8));
        VBox.setVgrow(transferTable, javafx.scene.layout.Priority.ALWAYS);

        Tab listTab = new Tab(text("plugin.certificates.tab.list", "Übersicht", "Overview"), listPane);
        Tab transferTab = new Tab(text("plugin.certificates.tab.transfer", "Übermittlung", "Transfer"), transferPane);
        TabPane tabs = new TabPane(listTab, transferTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        rootPane.setCenter(tabs);
    }

    private static <T> TableColumn<T, String> column(String id, String title, double width, java.util.function.Function<T, String> value) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setId(id);
        c.setPrefWidth(width);
        c.setCellValueFactory(p -> new ReadOnlyStringWrapper(value.apply(p.getValue())));
        return c;
    }

    static String scheduleText(String schedule) {
        switch (TransferSchedule.Schedule.of(schedule)) {
            case DAILY:
                return text("plugin.certificates.schedule.daily", "Täglich", "Daily");
            case WEEKLY:
                return text("plugin.certificates.schedule.weekly", "Wöchentlich", "Weekly");
            case MONTHLY:
                return text("plugin.certificates.schedule.monthly", "Monatlich", "Monthly");
            case ON_CHANGE:
                return text("plugin.certificates.schedule.onchange", "Bei Änderung", "On change");
            default:
                return text("plugin.certificates.schedule.manual", "Nur manuell", "Manual only");
        }
    }

    static String lastText(TransferSchedule.LastTransfer last) {
        if (last.getAt() == null) return "";
        String at = new DateTime(last.getAt()).toString("dd.MM.yyyy HH:mm");
        return last.isSuccess() ? at + ": " + String.format(text("plugin.certificates.transfer.ok", "%d Ausweise", "%d certificates"), last.getCount() != null ? last.getCount() : 0)
                : at + ": " + text("plugin.certificates.transfer.error", "Fehler", "Error") + " – " + nz(last.getError());
    }

    private void updateFilter() {
        int status = statusFilter.getSelectionModel().getSelectedIndex();
        String search = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        filteredRows.setPredicate(row -> {
            CertificateLogic.Status s = row.status();
            boolean match;
            switch (status) {
                case 1:
                    match = s == CertificateLogic.Status.EXPIRED || s == CertificateLogic.Status.EXPIRING;
                    break;
                case 2:
                    match = s == CertificateLogic.Status.EXPIRED;
                    break;
                case 3:
                    match = s == CertificateLogic.Status.EXPIRING;
                    break;
                case 4:
                    match = s == CertificateLogic.Status.VALID;
                    break;
                default:
                    match = true;
            }
            if (!match) return false;
            if (search.isEmpty()) return true;
            EnergyCertificate c = row.certificate;
            return (row.building + " " + nz(c.getRegistrationNumber()) + " " + nz(c.getIssuer()) + " " + nz(c.getNote()) + " "
                    + names.names(c.getResponsible())).toLowerCase().contains(search);
        });
    }

    @Override
    protected ToolBar toolbar() {
        return toolBar;
    }

    @Override
    protected void reload() {
        Task<Object[]> task = new Task<Object[]>() {
            @Override
            protected Object[] call() throws Exception {
                List<Row> loaded = new ArrayList<>();
                for (JEVisObject object : EntryStore.load(ds, EnergyCertificate.CLASS_NAME)) {
                    EnergyCertificate c = CertificateLogic.fromJson(EntryStore.read(object, EnergyCertificate.ATTRIBUTE_DATA));
                    if (c == null) {
                        c = new EnergyCertificate();
                    }
                    CertificateState state = CertificateState.fromJson(EntryStore.read(object, EnergyCertificate.ATTRIBUTE_TRANSFER_STATE));
                    JEVisAttribute file = object.getAttribute(EnergyCertificate.ATTRIBUTE_FILE);
                    loaded.add(new Row(object, c, state, buildingOf(object), file != null ? file.getLatestSample() : null));
                }
                List<TransferRow> targets = new ArrayList<>();
                for (JEVisObject object : EntryStore.load(ds, CLASS_TRANSFER)) {
                    Map<String, String> values = new HashMap<>();
                    for (String a : Arrays.asList("Enabled", "Channel", "Format", "Schedule", "Requested", "Last Transfer")) {
                        values.put(a, EntryStore.read(object, a));
                    }
                    targets.add(new TransferRow(object, values));
                }
                return new Object[]{loaded, targets};
            }
        };
        task.setOnSucceeded(event -> {
            names.clear();
            @SuppressWarnings("unchecked") List<Row> loaded = (List<Row>) task.getValue()[0];
            @SuppressWarnings("unchecked") List<TransferRow> targets = (List<TransferRow>) task.getValue()[1];
            rows.setAll(loaded);
            transfers.setAll(targets);
            updateFilter();
        });
        task.setOnFailed(event -> logger.error("Could not load energy certificates", task.getException()));
        Thread thread = new Thread(task, "Load energy certificates");
        thread.setDaemon(true);
        thread.start();
    }

    /** @return name of the nearest building (or organisation) above the certificate */
    static String buildingOf(JEVisObject object) {
        try {
            JEVisObject p = object;
            for (int i = 0; i < 50; i++) {
                List<JEVisObject> parents = p.getParents();
                if (parents == null || parents.isEmpty()) break;
                p = parents.get(0);
                if ("Building".equals(p.getJEVisClassName()) || "Organization".equals(p.getJEVisClassName())) return p.getName();
            }
        } catch (Exception e) {
            logger.debug("No building of {}", object, e);
        }
        return "";
    }

    // ---------------------------------------------------------------- create, edit, delete

    private void create() {
        JEVisObject building = chooseBuilding();
        if (building == null) return;
        EnergyCertificate c = new EnergyCertificate();
        c.setType(EnergyCertificate.Type.CONSUMPTION);
        CertificateForm form = new CertificateForm(ds, c, null, null);
        open(text("plugin.certificates.new", "Neuer Energieausweis", "New energy certificate"), building.getName(), form, c, () -> {
            try {
                JEVisObject directory = directoryOf(building, true);
                c.setCreated(new DateTime().toString("yyyy-MM-dd'T'HH:mm:ss"));
                c.setCreatedBy(EntryStore.currentUserName(ds));
                JEVisObject object = EntryStore.create(directory, EnergyCertificate.CLASS_NAME, objectName(c));
                EntryStore.write(object, EnergyCertificate.ATTRIBUTE_DATA, CertificateLogic.toJson(c));
                reload();
                return true;
            } catch (Exception e) {
                EntryStore.showError(text("plugin.certificates.error.save", "Der Energieausweis konnte nicht gespeichert werden.", "The energy certificate could not be saved."), e);
                return false;
            }
        });
    }

    private void editSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null) return;
        EnergyCertificate c = CertificateLogic.fromJson(CertificateLogic.toJson(row.certificate));
        boolean canWrite = EntryStore.canWrite(ds, row.object);
        FormLayout.FileRow[] fileRow = new FormLayout.FileRow[1];
        fileRow[0] = FormLayout.fileRow(row.fileName, row.fileName != null ? () -> download(row) : null, () -> {
            if (canWrite) upload(row, fileRow[0]);
        });
        String last = row.state.getLastReminder() != null ? row.state.getLastReminder().replace('T', ' ') : null;
        CertificateForm form = new CertificateForm(ds, c, fileRow[0], last);
        open(row.building + " · " + (c.getType() != null ? typeText(c.getType()) : ""), row.object.getName(), form, c, () -> {
            try {
                EntryStore.write(row.object, EnergyCertificate.ATTRIBUTE_DATA, CertificateLogic.toJson(c));
                EntryStore.rename(row.object, objectName(c));
                reload();
                return true;
            } catch (Exception e) {
                EntryStore.showError(text("plugin.certificates.error.save", "Der Energieausweis konnte nicht gespeichert werden.", "The energy certificate could not be saved."), e);
                return false;
            }
        });
        SidePanel.get().setReadOnly(!canWrite);
    }

    private void open(String title, String subtitle, CertificateForm form, EnergyCertificate c, java.util.function.BooleanSupplier onSave) {
        SidePanel panel = SidePanel.get();
        boolean shown = panel.show(title, subtitle, form.getForm(), () -> {
            String error = form.apply(c);
            if (error != null) {
                Notify.error(error);
                return false;
            }
            return onSave.getAsBoolean();
        });
        if (!shown) return;
        String snapshot = state(form, c);
        panel.setDirtyCheck(() -> !state(form, c).equals(snapshot));
    }

    private static String state(CertificateForm form, EnergyCertificate c) {
        EnergyCertificate copy = CertificateLogic.fromJson(CertificateLogic.toJson(c));
        if (copy == null) return "";
        form.apply(copy);
        return CertificateLogic.toJson(copy);
    }

    private void deleteSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null) return;
        if (Confirm.ask(String.format(text("plugin.certificates.delete.question", "Energieausweis „%s“ löschen?", "Delete energy certificate “%s”?"), row.building + " · " + row.object.getName()),
                text("plugin.certificates.delete.consequence", "Der Ausweis kommt in den Papierkorb und lässt sich von dort wiederherstellen.",
                        "The certificate moves to the recycle bin and can be restored from there."), text("dialog.ui.delete", "Löschen", "Delete"), true)) {
            try {
                ds.deleteObject(row.object.getID(), false);
                reload();
            } catch (Exception e) {
                EntryStore.showError(text("plugin.certificates.error.delete", "Der Energieausweis konnte nicht gelöscht werden.", "The energy certificate could not be deleted."), e);
            }
        }
    }

    static String objectName(EnergyCertificate c) {
        LocalDate issued = CertificateLogic.date(c.getIssued());
        String type = c.getType() == EnergyCertificate.Type.DEMAND ? "Bedarfsausweis" : "Verbrauchsausweis";
        return issued != null ? type + " " + issued.getYear() : type;
    }

    /** a building the user may add certificates to (directory there with create right, or create right on the building) */
    private JEVisObject chooseBuilding() {
        List<JEVisObject> buildings = new ArrayList<>();
        try {
            JEVisClass buildingClass = ds.getJEVisClass("Building");
            for (JEVisObject b : ds.getObjects(buildingClass, false)) {
                JEVisObject dir = directoryOf(b, false);
                if (dir != null ? ds.getCurrentUser().canCreate(dir.getID()) : ds.getCurrentUser().canCreate(b.getID())) buildings.add(b);
            }
        } catch (Exception e) {
            logger.error("Could not load buildings", e);
        }
        if (buildings.isEmpty()) {
            Notify.info(text("plugin.certificates.nobuilding", "Sie haben bei keinem Gebäude das Recht, Energieausweise anzulegen.", "You may not add energy certificates to any building."));
            return null;
        }
        if (buildings.size() == 1) return buildings.get(0);
        Map<String, JEVisObject> byLabel = new LinkedHashMap<>();
        for (JEVisObject b : buildings) byLabel.put(EntryStore.directoryLabel(b), b);
        List<String> labels = new ArrayList<>(byLabel.keySet());
        Collections.sort(labels);
        ChoiceDialog<String> dialog = new ChoiceDialog<>(labels.get(0), labels);
        dialog.initOwner(JEConfig.getStage());
        org.jevis.jeconfig.TopMenu.applyActiveTheme(dialog.getDialogPane().getScene());
        dialog.setTitle(text("plugin.certificates.new", "Neuer Energieausweis", "New energy certificate"));
        dialog.setHeaderText(text("plugin.certificates.choosebuilding", "Für welches Gebäude?", "For which building?"));
        return dialog.showAndWait().map(byLabel::get).orElse(null);
    }

    private JEVisObject directoryOf(JEVisObject building, boolean create) throws JEVisException {
        JEVisClass dirClass = ds.getJEVisClass(EnergyCertificate.DIRECTORY_CLASS_NAME);
        for (JEVisObject child : building.getChildren(dirClass, false)) return child;
        if (!create) return null;
        JEVisObject dir = building.buildObject(text("plugin.certificates.directory", "Energieausweise", "Energy certificates"), dirClass);
        dir.commit();
        return dir;
    }

    // ---------------------------------------------------------------- file

    private void download(Row row) {
        try {
            JEVisFile file = row.file.getValueAsFile();
            FileChooser chooser = new FileChooser();
            chooser.setInitialFileName(row.fileName);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            File target = chooser.showSaveDialog(JEConfig.getStage());
            if (target != null) file.saveToFile(target);
        } catch (Exception e) {
            EntryStore.showError(text("plugin.certificates.error.file", "Die Datei konnte nicht geladen werden.", "The file could not be loaded."), e);
        }
    }

    private void upload(Row row, FormLayout.FileRow fileRow) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File source = chooser.showOpenDialog(JEConfig.getStage());
        if (source == null) return;
        try {
            byte[] bytes = Files.readAllBytes(source.toPath());
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
                Notify.error(text("plugin.certificates.error.notpdf", "Bitte eine PDF-Datei wählen.", "Please choose a PDF file."));
                return;
            }
            if (bytes.length > 20 * 1024 * 1024) {
                Notify.error(text("plugin.certificates.error.toolarge", "Das PDF ist größer als 20 MB.", "The PDF is larger than 20 MB."));
                return;
            }
            JEVisAttribute attribute = row.object.getAttribute(EnergyCertificate.ATTRIBUTE_FILE);
            JEVisSample sample = attribute.buildSample(new DateTime(), new JEVisFileImp(source.getName(), bytes));
            sample.commit();
            fileRow.setFileName(source.getName(), false);
            Notify.info(text("plugin.certificates.file.uploaded", "Das PDF wurde gespeichert.", "The PDF was saved."));
            reload();
        } catch (Exception e) {
            EntryStore.showError(text("plugin.certificates.error.file", "Die Datei konnte nicht gespeichert werden.", "The file could not be saved."), e);
        }
    }

    // ---------------------------------------------------------------- transfer and export

    private void requestTransfer() {
        TransferRow row = transferTable.getSelectionModel().getSelectedItem();
        if (row == null) return;
        try {
            JEVisAttribute requested = row.object.getAttribute("Requested");
            String value = "{\"at\":\"" + new DateTime().toString("yyyy-MM-dd'T'HH:mm:ss") + "\",\"by\":\""
                    + ds.getCurrentUser().getAccountName().replace("\"", "") + "\"}";
            requested.buildSample(new DateTime(), value).commit();
            Notify.info(String.format(text("plugin.certificates.transfer.requestedok",
                    "Die Übermittlung an „%s“ wurde angefordert. Sie erfolgt mit dem nächsten Lauf des Dienstes.",
                    "The transfer to “%s” was requested. It runs with the next run of the service."), row.object.getName()));
            reload();
        } catch (Exception e) {
            EntryStore.showError(text("plugin.certificates.error.transfer", "Die Übermittlung konnte nicht angefordert werden.", "The transfer could not be requested."), e);
        }
    }

    private void exportCsv() {
        List<CertificateExport.Entry> entries = new ArrayList<>();
        for (Row r : table.getItems()) {
            entries.add(new CertificateExport.Entry(r.object.getID(), r.building, r.certificate, r.fileName, null));
        }
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(CertificateExport.fileName(CertificateExport.Format.CSV, new DateTime()));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File target = chooser.showSaveDialog(JEConfig.getStage());
        if (target == null) return;
        try {
            Files.write(target.toPath(), CertificateExport.csv(entries, new LocalDate()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            EntryStore.showError(text("plugin.certificates.error.export", "Der Export ist fehlgeschlagen.", "The export failed."), e);
        }
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    @Override
    public String getClassName() {
        return PLUGIN_NAME;
    }

    @Override
    public Region getIcon() {
        return JEConfig.getSVGImage(Icon.BUILDING, Plugin.IconSize, Plugin.IconSize, Icon.CSS_PLUGIN);
    }

    @Override
    public int getPrefTapPos() {
        return 13;
    }
}
