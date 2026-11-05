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
package org.jevis.jeconfig.dialog;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.ws.json.JsonUserLogEntry;
import org.jevis.commons.ws.sql.userlog.UserLogUtil;
import org.jevis.jeapi.ws.JEVisDataSourceWS;
import org.joda.time.DateTime;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Shows the user activity log (OpenJEVis #2341). Sys Admins can select a user or all users, other users see their
 * own log. With an older webservice the log is read from the samples of the attribute {@code Activities}.
 */
public class UserLogDialog {

    private static final Logger logger = LogManager.getLogger(UserLogDialog.class);
    private static final int PAGE_SIZE = 200;
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final JEVisDataSource ds;
    private final ObservableList<JsonUserLogEntry> rows = FXCollections.observableArrayList();
    private final ComboBox<JEVisObject> userBox = new ComboBox<>();
    private final DatePicker fromPicker = new DatePicker(LocalDate.now().minusDays(30));
    private final DatePicker untilPicker = new DatePicker();
    private final Button previous = new Button(t("app.userlog.previous"));
    private final Button next = new Button(t("app.userlog.next"));
    private final Label pageLabel = new Label();
    private final Label infoLabel = new Label();
    private final Window owner;
    private boolean sysAdmin;
    private JEVisObject ownUser;
    private int page = 0;

    private UserLogDialog(Window owner, JEVisDataSource ds) {
        this.owner = owner;
        this.ds = ds;
    }

    private static String t(String key) {
        return I18n.getInstance().getString(key);
    }

    public static void show(Window owner, JEVisDataSource ds) {
        new UserLogDialog(owner, ds).showDialog();
    }

    private static Long toMillis(LocalDate date, boolean endOfDay) {
        if (date == null) return null;
        LocalDate day = endOfDay ? date.plusDays(1) : date;
        long ms = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return endOfDay ? ms - 1 : ms;
    }

    private static TableColumn<JsonUserLogEntry, String> column(String title, double width, Function<JsonUserLogEntry, String> value) {
        TableColumn<JsonUserLogEntry, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void showDialog() {
        try {
            JEVisUser current = ds.getCurrentUser();
            sysAdmin = current.isSysAdmin();
            ownUser = current.getUserObject();
        } catch (Exception ex) {
            logger.error("Could not read the current user", ex);
        }

        userBox.setConverter(new StringConverter<JEVisObject>() {
            @Override
            public String toString(JEVisObject object) {
                return object == null ? t("app.userlog.allusers") : object.getName();
            }

            @Override
            public JEVisObject fromString(String string) {
                return null;
            }
        });
        userBox.setButtonCell(new ListCell<JEVisObject>() {
            @Override
            protected void updateItem(JEVisObject item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : userBox.getConverter().toString(item));
            }
        });
        List<JEVisObject> users = new ArrayList<>();
        if (sysAdmin) {
            users.add(null);
            users.addAll(loadUsers());
        } else if (ownUser != null) {
            users.add(ownUser);
        }
        userBox.getItems().setAll(users);
        userBox.getSelectionModel().select(sysAdmin ? null : ownUser);
        userBox.setDisable(!sysAdmin);

        TableView<JsonUserLogEntry> table = new TableView<>(rows);
        table.getColumns().add(column(t("app.userlog.time"), 150,
                e -> e.getTimestamp() == null ? "" : new DateTime(e.getTimestamp()).toString(TIME_PATTERN)));
        table.getColumns().add(column(t("app.userlog.user"), 140, e -> e.getUserName() != null ? e.getUserName() : String.valueOf(e.getUserId())));
        table.getColumns().add(column(t("app.userlog.action"), 170, JsonUserLogEntry::getAction));
        table.getColumns().add(column(t("app.userlog.object"), 90, e -> e.getObjectId() == null ? "" : String.valueOf(e.getObjectId())));
        table.getColumns().add(column(t("app.userlog.details"), 420, JsonUserLogEntry::getDetails));
        table.setPrefSize(980, 480);
        VBox.setVgrow(table, Priority.ALWAYS);

        Button reload = new Button(t("app.userlog.reload"));
        reload.setOnAction(event -> {
            page = 0;
            load();
        });
        previous.setOnAction(event -> {
            if (page > 0) {
                page--;
                load();
            }
        });
        next.setOnAction(event -> {
            page++;
            load();
        });

        HBox filter = new HBox(8, new Label(t("app.userlog.user")), userBox,
                new Label(t("app.userlog.from")), fromPicker, new Label(t("app.userlog.until")), untilPicker, reload);
        filter.setAlignment(Pos.CENTER_LEFT);
        HBox paging = new HBox(8, previous, pageLabel, next, infoLabel);
        paging.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(10, filter, table, paging);
        content.setPadding(new Insets(10));

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(t("app.userlog.title"));
        dialog.setResizable(true);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        load();
        dialog.showAndWait();
    }

    private List<JEVisObject> loadUsers() {
        List<JEVisObject> users = new ArrayList<>();
        try {
            JEVisClass userClass = ds.getJEVisClass("User");
            users.addAll(ds.getObjects(userClass, false));
            users.sort(Comparator.comparing(JEVisObject::getName, String.CASE_INSENSITIVE_ORDER));
        } catch (Exception ex) {
            logger.error("Could not load users", ex);
        }
        return users;
    }

    private void load() {
        JEVisObject user = userBox.getValue();
        Long userId = user != null ? user.getID() : null;
        Long from = toMillis(fromPicker.getValue(), false);
        Long until = toMillis(untilPicker.getValue(), true);
        int offset = page * PAGE_SIZE;
        previous.setDisable(true);
        next.setDisable(true);

        Task<LoadResult> task = new Task<LoadResult>() {
            @Override
            protected LoadResult call() throws Exception {
                List<JsonUserLogEntry> entries = null;
                if (ds instanceof JEVisDataSourceWS) {
                    entries = ((JEVisDataSourceWS) ds).getUserLog(userId, from, until, offset, PAGE_SIZE);
                }
                if (entries != null) return new LoadResult(entries, false);
                List<JEVisObject> sources = new ArrayList<>();
                if (user != null) {
                    sources.add(user);
                } else {
                    for (JEVisObject object : userBox.getItems()) {
                        if (object != null) sources.add(object);
                    }
                }
                return new LoadResult(UserLogUtil.mergePage(offset, PAGE_SIZE, readLegacy(sources, from, until)), true);
            }
        };
        task.setOnSucceeded(event -> {
            LoadResult result = task.getValue();
            rows.setAll(result.entries);
            pageLabel.setText(t("app.userlog.page") + " " + (page + 1));
            previous.setDisable(page == 0);
            next.setDisable(result.entries.size() < PAGE_SIZE);
            infoLabel.setText(result.legacy ? t("app.userlog.legacy") : "");
        });
        task.setOnFailed(event -> {
            logger.error("Could not load the user log", task.getException());
            previous.setDisable(page == 0);
            Alert alert = new Alert(Alert.AlertType.ERROR, task.getException() != null ? task.getException().getMessage() : "", ButtonType.OK);
            alert.initOwner(owner);
            alert.setHeaderText(t("app.userlog.error"));
            alert.showAndWait();
        });
        Thread thread = new Thread(task, "user-log-load");
        thread.setDaemon(true);
        thread.start();
    }

    private List<JsonUserLogEntry> readLegacy(List<JEVisObject> users, Long from, Long until) {
        List<JsonUserLogEntry> entries = new ArrayList<>();
        for (JEVisObject user : users) {
            try {
                JEVisAttribute attribute = user.getAttribute(UserLogUtil.LEGACY_ATTRIBUTE);
                if (attribute == null) continue;
                List<JEVisSample> samples = attribute.getSamples(from != null ? new DateTime(from) : null,
                        until != null ? new DateTime(until) : null);
                for (JEVisSample sample : samples) {
                    entries.add(UserLogUtil.fromLegacySample(user.getID(), user.getName(),
                            sample.getTimestamp().getMillis(), sample.getValueAsString()));
                }
            } catch (Exception ex) {
                logger.error("Could not read the activities of {}", user.getName(), ex);
            }
        }
        return entries;
    }

    private static class LoadResult {
        private final List<JsonUserLogEntry> entries;
        private final boolean legacy;

        LoadResult(List<JsonUserLogEntry> entries, boolean legacy) {
            this.entries = entries;
            this.legacy = legacy;
        }
    }
}
