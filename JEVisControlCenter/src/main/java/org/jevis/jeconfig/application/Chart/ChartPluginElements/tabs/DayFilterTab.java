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
package org.jevis.jeconfig.application.Chart.ChartPluginElements.tabs;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.application.Chart.data.DataModel;
import org.jevis.jeconfig.application.Chart.data.DayFilter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Settings tab for the day filter of an analysis (OpenJEVis issue #1448): excluded weekdays,
 * public holidays and single dates. Changes are written directly into the {@link DataModel};
 * the chart is updated when the settings dialog is confirmed.
 */
public class DayFilterTab extends Tab {

    public DayFilterTab(DataModel dataModel) {
        super(I18n.getInstance().getString("graph.tabs.tab.dayfilter"));
        setClosable(false);

        Locale locale = I18n.getInstance().getLocale();

        Label explanation = new Label(I18n.getInstance().getString("graph.tabs.dayfilter.explanation"));
        explanation.setWrapText(true);
        explanation.setMaxWidth(520);

        /* weekdays */
        Label weekdaysLabel = new Label(I18n.getInstance().getString("graph.tabs.dayfilter.weekdays"));
        FlowPane weekdaysPane = new FlowPane(10, 5);
        Set<Integer> excludedWeekdays = new TreeSet<>(DayFilter.parseWeekdays(dataModel.getDayFilterWeekdays()));
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            CheckBox checkBox = new CheckBox(dayOfWeek.getDisplayName(TextStyle.FULL, locale));
            checkBox.setSelected(excludedWeekdays.contains(dayOfWeek.getValue()));
            checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    excludedWeekdays.add(dayOfWeek.getValue());
                } else {
                    excludedWeekdays.remove(dayOfWeek.getValue());
                }
                dataModel.setDayFilterWeekdays(DayFilter.formatWeekdays(excludedWeekdays));
            });
            weekdaysPane.getChildren().add(checkBox);
        }

        /* holidays */
        CheckBox holidaysBox = new CheckBox(I18n.getInstance().getString("graph.tabs.dayfilter.holidays"));
        holidaysBox.setSelected(dataModel.isDayFilterHolidays());
        holidaysBox.selectedProperty().addListener((observable, oldValue, newValue) -> dataModel.setDayFilterHolidays(newValue));

        /* single dates */
        Label datesLabel = new Label(I18n.getInstance().getString("graph.tabs.dayfilter.dates"));
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
        ObservableList<LocalDate> dates = FXCollections.observableArrayList(DayFilter.parseDates(dataModel.getDayFilterDates()));
        ListView<LocalDate> datesList = new ListView<>(dates);
        datesList.setPrefHeight(140);
        datesList.setMaxWidth(260);
        datesList.setCellFactory(param -> new ListCell<LocalDate>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.format(dateFormatter) + " (" + item.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale) + ")");
                }
            }
        });

        DatePicker datePicker = new DatePicker();
        Button addButton = new Button("", JEConfig.getSVGImage(Icon.PLUS, 12, 12));
        Button removeButton = new Button("", JEConfig.getSVGImage(Icon.MINUS, 12, 12));
        removeButton.disableProperty().bind(datesList.getSelectionModel().selectedItemProperty().isNull());

        addButton.setOnAction(event -> {
            LocalDate date = datePicker.getValue();
            if (date != null && !dates.contains(date)) {
                dates.add(date);
                FXCollections.sort(dates);
                dataModel.setDayFilterDates(DayFilter.formatDates(dates));
            }
        });
        removeButton.setOnAction(event -> {
            LocalDate selected = datesList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                dates.remove(selected);
                dataModel.setDayFilterDates(DayFilter.formatDates(dates));
            }
        });

        HBox dateInput = new HBox(5, datePicker, addButton, removeButton);
        dateInput.setAlignment(Pos.CENTER_LEFT);

        /* aggregation hint */
        Label hint = new Label(I18n.getInstance().getString("graph.tabs.dayfilter.hint"));
        hint.setWrapText(true);
        hint.setMaxWidth(520);
        hint.setStyle("-fx-font-style: italic;");

        VBox vBox = new VBox(8,
                explanation,
                new Separator(),
                weekdaysLabel, weekdaysPane,
                holidaysBox,
                new Separator(),
                datesLabel, dateInput, datesList,
                new Separator(),
                hint);
        vBox.setPadding(new Insets(10));

        setContent(vBox);
    }
}
