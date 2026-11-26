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
package org.jevis.jeconfig.plugin.alarms;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.alarm.AlarmConfiguration;
import org.jevis.commons.alarm.OutageEvent;
import org.jevis.commons.alarm.OutageLogic;
import org.jevis.commons.alarm.OutageState;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jevis.jeconfig.application.navigation.PluginSidebar.text;

/**
 * Data outages and transmission faults (docs/ALARMS-OUTAGE.md) above the alarm list: open ones from "Outage State",
 * ended ones from "Outage Log" in the date range of the plugin. Hidden when there are none.
 */
class OutagePane extends VBox {

    private static final Logger logger = LogManager.getLogger(OutagePane.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static class Row {
        final boolean open;
        final String object;
        final String kind;
        final String since;
        final String allowed;
        final String cause;
        final String end;
        final String configuration;

        Row(boolean open, String object, String kind, String since, String allowed, String cause, String end, String configuration) {
            this.open = open;
            this.object = object;
            this.kind = kind;
            this.since = since;
            this.allowed = allowed;
            this.cause = cause;
            this.end = end;
            this.configuration = configuration;
        }
    }

    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final TableView<Row> table = new TableView<>(rows);
    private final JEVisDataSource ds;

    OutagePane(JEVisDataSource ds) {
        super(6);
        this.ds = ds;
        setPadding(new Insets(8, 8, 4, 8));
        Label title = new Label(text("plugin.alarms.outages", "Datenausfälle", "Data outages"));
        title.getStyleClass().add("suc-section-title");
        title.setStyle("-fx-font-weight: bold;");
        table.getColumns().addAll(Arrays.asList(
                column(text("plugin.alarms.outages.status", "Status", "Status"), 90, r -> r.open ? text("plugin.alarms.outages.open", "Offen", "Open") : text("plugin.alarms.outages.ended", "Beendet", "Ended")),
                column(text("plugin.alarms.outages.object", "Datenreihe / Datenquelle", "Data row / data source"), 280, r -> r.object),
                column(text("plugin.alarms.outages.kind", "Art", "Type"), 170, r -> r.kind),
                column(text("plugin.alarms.outages.since", "Letzter Wert", "Latest value"), 140, r -> r.since),
                column(text("plugin.alarms.outages.allowed", "Erlaubt ohne Daten", "Allowed without data"), 130, r -> r.allowed),
                column(text("plugin.alarms.outages.cause", "Ursache", "Cause"), 180, r -> r.cause),
                column(text("plugin.alarms.outages.end", "Ende", "End"), 220, r -> r.end),
                column(text("plugin.alarms.outages.configuration", "Alarmkonfiguration", "Alarm configuration"), 180, r -> r.configuration)));
        table.setRowFactory(param -> new TableRow<Row>() {
            @Override
            protected void updateItem(Row item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("outage-open"), item != null && !empty && item.open);
            }
        });
        table.setPrefHeight(150);
        table.setPlaceholder(new Label(""));
        getChildren().addAll(title, table);
        setVisible(false);
        setManaged(false);
    }

    private static TableColumn<Row, String> column(String title, double width, java.util.function.Function<Row, String> value) {
        TableColumn<Row, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(p -> new ReadOnlyStringWrapper(value.apply(p.getValue())));
        return c;
    }

    /** loads the outages of the configurations in the background */
    void load(List<AlarmConfiguration> configurations, DateTime from, DateTime to) {
        Thread thread = new Thread(() -> {
            List<Row> open = new ArrayList<>();
            List<Row> ended = new ArrayList<>();
            for (AlarmConfiguration configuration : configurations) {
                try {
                    JEVisObject cfg = configuration.getObject();
                    JEVisAttribute state = cfg.getAttribute(OutageState.ATTRIBUTE);
                    if (state == null) continue;
                    JEVisSample latest = state.getLatestSample();
                    for (OutageState.Open o : OutageState.fromJson(latest != null ? latest.getValueAsString() : null).getOpen().values()) {
                        open.add(new Row(true, name(o.object), kind(o.kind), time(o.since), duration(o.minutes), cause(o.cause), "", configuration.getName()));
                    }
                    JEVisAttribute log = cfg.getAttribute(OutageEvent.ATTRIBUTE);
                    if (log == null) continue;
                    for (JEVisSample s : log.getSamples(from, to)) {
                        for (OutageEvent e : events(s.getValueAsString())) {
                            if (!OutageEvent.END.equals(e.event)) continue;
                            String result = e.resumed == null ? text("plugin.alarms.outages.unmonitored", "nicht mehr überwacht", "no longer monitored")
                                    : time(e.resumed) + " – " + (Boolean.TRUE.equals(e.backfilled)
                                    ? text("plugin.alarms.outages.late", "verspätet geliefert", "delivered late")
                                    : text("plugin.alarms.outages.gap", "Lücke bleibt", "gap remains"));
                            ended.add(new Row(false, name(e.object), kind(e.kind), time(e.since), duration(e.minutes), cause(e.cause), result, configuration.getName()));
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Could not read the outages of {}", configuration.getName(), e);
                }
            }
            List<Row> all = new ArrayList<>(open);
            all.addAll(ended);
            Platform.runLater(() -> {
                rows.setAll(all);
                setVisible(!all.isEmpty());
                setManaged(!all.isEmpty());
            });
        }, "Load outages");
        thread.setDaemon(true);
        thread.start();
    }

    static List<OutageEvent> events(String json) {
        List<OutageEvent> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        try {
            if (json.trim().startsWith("[")) list.addAll(Arrays.asList(MAPPER.readValue(json, OutageEvent[].class)));
            else {
                OutageEvent e = OutageEvent.fromJson(json);
                if (e != null) list.add(e);
            }
        } catch (Exception e) {
            logger.debug("Could not read outage log {}", json, e);
        }
        return list;
    }

    private String name(long id) {
        try {
            JEVisObject o = ds.getObject(id);
            if (o == null) return "#" + id;
            if ("Clean Data".equals(o.getJEVisClassName()) && !o.getParents().isEmpty()) return o.getParents().get(0).getName() + " / " + o.getName();
            return o.getName();
        } catch (Exception e) {
            return "#" + id;
        }
    }

    private String cause(Long id) {
        return id == null ? "" : text("plugin.alarms.outages.source", "Datenquelle", "Data source") + " " + name(id);
    }

    static String kind(String kind) {
        return "SOURCE".equals(kind) ? text("plugin.alarms.outages.kind.source", "Übertragungsstörung", "Transmission fault")
                : text("plugin.alarms.outages.kind.row", "Datenausfall", "Data outage");
    }

    static String time(String iso) {
        DateTime t = OutageLogic.parse(iso);
        return t != null ? t.toString("dd.MM.yyyy HH:mm") : text("plugin.alarms.outages.never", "noch nie", "never");
    }

    static String duration(int minutes) {
        if (minutes % 1440 == 0) return (minutes / 1440) + " " + (minutes == 1440 ? text("plugin.alarms.day", "Tag", "day") : text("plugin.alarms.days", "Tage", "days"));
        if (minutes % 60 == 0) return (minutes / 60) + " " + (minutes == 60 ? text("plugin.alarms.hour", "Stunde", "hour") : text("plugin.alarms.hours", "Stunden", "hours"));
        return minutes + " " + text("plugin.alarms.minutes", "Minuten", "minutes");
    }
}
