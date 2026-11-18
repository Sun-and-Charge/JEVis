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
package org.jevis.jeconfig.application.control;

import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Time input of the Control Center (replaces the JFoenix {@code JFXTimePicker}, J25): an editable list of the times
 * of the day in steps of 15 minutes; any other time can be typed (H:mm or H:mm:ss). Always 24 hours.
 */
public class TimePicker extends ComboBox<LocalTime> {
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int STEP_MINUTES = 15;

    public TimePicker() {
        this(null);
    }

    public TimePicker(LocalTime time) {
        for (int minute = 0; minute < 24 * 60; minute += STEP_MINUTES) {
            getItems().add(LocalTime.of(minute / 60, minute % 60));
        }
        setEditable(true);
        setVisibleRowCount(12);
        setConverter(new StringConverter<LocalTime>() {
            @Override
            public String toString(LocalTime value) {
                return format(value);
            }

            @Override
            public LocalTime fromString(String text) {
                LocalTime parsed = parse(text);
                return parsed != null ? parsed : getValue();
            }
        });
        getStyleClass().add("time-picker");
        setValue(time);
    }

    /** JFoenix compatibility: the picker always shows 24 hours. */
    public void set24HourView(boolean value) {
    }

    /** "HH:mm", or "HH:mm:ss" when the time has seconds; empty for null */
    public static String format(LocalTime value) {
        if (value == null) return "";
        return value.getSecond() != 0 ? value.format(LONG) : value.format(SHORT);
    }

    /** a time typed as H, H:mm or H:mm:ss (also with "." as separator); null if it is no time */
    public static LocalTime parse(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String[] parts = text.trim().replace('.', ':').split(":");
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            int second = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
            if (parts.length > 3) return null;
            return LocalTime.of(hour, minute, second);
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }
}
